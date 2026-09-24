package com.dsa.app.data

import com.dsa.app.util.nowMs

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 各平台 Settings 实例（Android=SharedPreferences / iOS=NSUserDefaults） */
expect fun createSettings(): Settings

/** 本地存储层（KMP 版，multiplatform-settings） */
class Store(private val settings: Settings = createSettings()) {

    private val json = Json { ignoreUnknownKeys = true }

    // ===== 自选股 =====
    fun getWatchlist(): List<String> =
        settings.getString(KEY_WATCHLIST, "").let { raw ->
            if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        }

    fun setWatchlist(codes: List<String>) {
        settings.putString(KEY_WATCHLIST, json.encodeToString(codes))
    }

    fun addStocks(codes: List<String>) {
        if (codes.isEmpty()) return
        val current = getWatchlist().toMutableList()
        codes.forEach { code -> if (!current.contains(code)) current.add(code) }
        setWatchlist(current)
    }

    fun removeStock(code: String) {
        setWatchlist(getWatchlist().filterNot { it == code })
    }

    // ===== 持仓账号 =====
    fun getAccounts(): List<HoldingAccount> =
        settings.getString(KEY_ACCOUNTS, "").let { raw ->
            if (raw.isBlank()) listOf(HoldingAccount()) else runCatching { json.decodeFromString<List<HoldingAccount>>(raw) }.getOrDefault(listOf(HoldingAccount()))
        }

    private fun saveAccounts(list: List<HoldingAccount>) {
        settings.putString(KEY_ACCOUNTS, json.encodeToString(list))
    }

    fun getCurrentAccountId(): String =
        settings.getString(KEY_CURRENT_ACCOUNT, "account_1")

    fun setCurrentAccountId(id: String) {
        settings.putString(KEY_CURRENT_ACCOUNT, id)
    }

    fun addAccount(name: String): String {
        val accounts = getAccounts().toMutableList()
        val id = "account_${nowMs()}"
        accounts.add(HoldingAccount(id = id, name = name))
        saveAccounts(accounts)
        return id
    }

    fun renameAccount(id: String, name: String) {
        saveAccounts(getAccounts().map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deleteAccount(id: String) {
        val accounts = getAccounts().filterNot { it.id == id }
        saveAccounts(if (accounts.isEmpty()) listOf(HoldingAccount()) else accounts)
        if (getCurrentAccountId() == id) setCurrentAccountId(accounts.first().id)
    }

    fun getCurrentAccount(): HoldingAccount =
        getAccounts().firstOrNull { it.id == getCurrentAccountId() } ?: HoldingAccount()

    fun addHoldingsToCurrentAccount(list: List<Holding>) {
        if (list.isEmpty()) return
        val accId = getCurrentAccountId()
        saveAccounts(getAccounts().map { acc ->
            if (acc.id == accId) {
                val merged = acc.holdings.toMutableList()
                list.forEach { h -> if (!merged.any { it.code == h.code }) merged.add(h) }
                acc.copy(holdings = merged)
            } else acc
        })
    }

    fun setCurrentAccountHoldings(list: List<Holding>) {
        val accId = getCurrentAccountId()
        saveAccounts(getAccounts().map { if (it.id == accId) it.copy(holdings = list) else it })
    }

    fun removeHoldingFromCurrentAccount(code: String) {
        val accId = getCurrentAccountId()
        saveAccounts(getAccounts().map { if (it.id == accId) it.copy(holdings = it.holdings.filterNot { h -> h.code == code }) else it })
    }

    fun updateHoldingInCurrentAccount(h: Holding) {
        val accId = getCurrentAccountId()
        saveAccounts(getAccounts().map { acc ->
            if (acc.id == accId) acc.copy(holdings = acc.holdings.map { if (it.code == h.code) h else it }) else acc
        })
    }

    // ===== AI 配置 =====
    fun getApiKeys(): Pair<String, String> =
        settings.getString(KEY_KEY1, "") to settings.getString(KEY_KEY2, "")

    fun setApiKeys(k1: String, k2: String) {
        settings.putString(KEY_KEY1, k1)
        settings.putString(KEY_KEY2, k2)
    }

    fun getModel(): String = settings.getString(KEY_MODEL, "THUDM/GLM-4-9B-0414")
    fun setModel(m: String) = settings.putString(KEY_MODEL, m)

    fun getTheme(): String = settings.getString(KEY_THEME, "dark")
    fun setTheme(t: String) = settings.putString(KEY_THEME, t)

    fun getAiProvider(): String = settings.getString(KEY_AI_PROVIDER, "siliconflow")
    fun setAiProvider(p: String) = settings.putString(KEY_AI_PROVIDER, p)

    fun getProviderKeys(): Map<String, String> =
        settings.getString(KEY_PROVIDER_KEYS, "").let { raw ->
            if (raw.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        }

    fun setProviderKey(provider: String, key: String) {
        val map = getProviderKeys().toMutableMap()
        if (key.isBlank()) map.remove(provider) else map[provider] = key
        settings.putString(KEY_PROVIDER_KEYS, json.encodeToString(map))
    }

    fun getCustomBaseUrl(): String = settings.getString(KEY_CUSTOM_URL, "")
    fun setCustomBaseUrl(url: String) = settings.putString(KEY_CUSTOM_URL, url)

    fun getThsApiKey(): String = settings.getString(KEY_THS_KEY, "")
    fun setThsApiKey(key: String) = settings.putString(KEY_THS_KEY, key)

    fun getDataSource(): String = settings.getString(KEY_DATA_SOURCE, "tencent")
    fun setDataSource(s: String) = settings.putString(KEY_DATA_SOURCE, s)

    fun getAutoAnalysisEnabled(): Boolean = settings.getBoolean(KEY_AUTO_ANALYSIS, false)
    fun setAutoAnalysisEnabled(b: Boolean) = settings.putBoolean(KEY_AUTO_ANALYSIS, b)

    // ===== 历史报告 =====
    fun getAnalysisReports(): List<AnalysisReport> =
        settings.getString(KEY_AI_REPORTS, "").let { raw ->
            if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<AnalysisReport>>(raw) }.getOrDefault(emptyList())
        }

    fun saveAnalysisReport(report: AnalysisReport) {
        val list = (getAnalysisReports() + report).takeLast(100)
        settings.putString(KEY_AI_REPORTS, json.encodeToString(list))
    }

    fun getPortfolioReports(): List<PortfolioReport> =
        settings.getString(KEY_PORTFOLIO_REPORTS, "").let { raw ->
            if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<PortfolioReport>>(raw) }.getOrDefault(emptyList())
        }

    fun savePortfolioReport(report: PortfolioReport) {
        val list = (getPortfolioReports() + report).takeLast(100)
        settings.putString(KEY_PORTFOLIO_REPORTS, json.encodeToString(list))
    }

    fun getHoldingSnapshots(): List<HoldingSnapshot> =
        settings.getString(KEY_SNAPSHOTS, "").let { raw ->
            if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<HoldingSnapshot>>(raw) }.getOrDefault(emptyList())
        }

    fun saveHoldingSnapshot(snapshot: HoldingSnapshot) {
        val list = (getHoldingSnapshots() + snapshot).takeLast(100)
        settings.putString(KEY_SNAPSHOTS, json.encodeToString(list))
    }

    fun getHoldingChangeReports(): List<HoldingChangeReport> =
        settings.getString(KEY_CHANGE_REPORTS, "").let { raw ->
            if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString<List<HoldingChangeReport>>(raw) }.getOrDefault(emptyList())
        }

    fun saveHoldingChangeReport(report: HoldingChangeReport) {
        val list = (getHoldingChangeReports() + report).takeLast(100)
        settings.putString(KEY_CHANGE_REPORTS, json.encodeToString(list))
    }

    companion object {
        private const val KEY_WATCHLIST = "watchlist"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_CURRENT_ACCOUNT = "current_account"
        private const val KEY_KEY1 = "api_key_1"
        private const val KEY_KEY2 = "api_key_2"
        private const val KEY_MODEL = "model"
        private const val KEY_THEME = "theme"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_PROVIDER_KEYS = "provider_keys"
        private const val KEY_CUSTOM_URL = "custom_base_url"
        private const val KEY_THS_KEY = "ths_api_key"
        private const val KEY_DATA_SOURCE = "data_source"
        private const val KEY_AUTO_ANALYSIS = "auto_analysis"
        private const val KEY_AI_REPORTS = "ai_reports"
        private const val KEY_PORTFOLIO_REPORTS = "portfolio_reports"
        private const val KEY_SNAPSHOTS = "holding_snapshots"
        private const val KEY_CHANGE_REPORTS = "holding_change_reports"
    }
}
