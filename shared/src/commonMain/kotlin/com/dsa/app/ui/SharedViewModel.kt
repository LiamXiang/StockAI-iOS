package com.dsa.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dsa.app.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime

/** 全局状态管理（KMP 版，替代 Android ViewModel） */
class SharedViewModel(
    private val store: Store = Store(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {

    // ===== 自选 / 持仓 =====
    var watchlist by mutableStateOf(store.getWatchlist())
        private set
    var holdings by mutableStateOf(store.getCurrentAccount().holdings)
        private set
    var accounts by mutableStateOf(store.getAccounts())
        private set
    var currentHoldingAccountId by mutableStateOf(store.getCurrentAccountId())
        private set

    // ===== 行情 =====
    var quotes by mutableStateOf<Map<String, Quote>>(emptyMap())
        private set
    var loadingQuotes by mutableStateOf(false)
        private set
    var refreshError by mutableStateOf<String?>(null)
        private set

    // ===== AI 配置 =====
    var apiKey1 by mutableStateOf(store.getApiKeys().first)
        private set
    var apiKey2 by mutableStateOf(store.getApiKeys().second)
        private set
    var model by mutableStateOf(store.getModel())
        private set
    var theme by mutableStateOf(store.getTheme())
        private set
    var aiProvider by mutableStateOf(store.getAiProvider())
        private set
    var providerKeys by mutableStateOf(store.getProviderKeys())
        private set
    var customBaseUrl by mutableStateOf(store.getCustomBaseUrl())
        private set
    var thsApiKey by mutableStateOf(store.getThsApiKey())
        private set
    var dataSource by mutableStateOf(store.getDataSource())
        private set
    var dataSourceFallback by mutableStateOf(false)
        private set
    var autoAnalysisEnabled by mutableStateOf(store.getAutoAnalysisEnabled())
        private set

    // ===== 报告历史 =====
    var analysisReports by mutableStateOf(store.getAnalysisReports())
        private set
    var portfolioReports by mutableStateOf(store.getPortfolioReports())
        private set
    var holdingSnapshots by mutableStateOf(store.getHoldingSnapshots())
        private set
    var holdingChangeReports by mutableStateOf(store.getHoldingChangeReports())
        private set

    // ===== 工具方法 =====
    fun normalizeCode(code: String): String {
        var c = code.trim().lowercase()
        c = c.removeSuffix(".sh").removeSuffix(".sz").removeSuffix(".bj")
        c = c.removePrefix("sh").removePrefix("sz").removePrefix("bj")
        return c
    }

    fun isCode(s: String): Boolean {
        val c = normalizeCode(s)
        return c.length == 6 && c.all { it.isDigit() }
    }

    suspend fun resolveStockCode(input: String): String? {
        val t = normalizeCode(input)
        if (t.isEmpty()) return null
        if (isCode(t)) return t
        val tencent = try {
            withContext(Dispatchers.IO) {
                MarketApi.searchStocks(input).firstOrNull()?.let { s -> s.code }
            }
        } catch (e: Exception) { null }
        if (tencent != null && isCode(tencent)) return tencent
        if (thsApiKey.isNotBlank()) {
            val ths = try {
                withContext(Dispatchers.IO) {
                    ThsApi.search(input, thsApiKey, 1).firstOrNull()?.code
                }
            } catch (e: Exception) { null }
            if (ths != null && isCode(ths)) return ths
        }
        return null
    }

    suspend fun resolveStockCodes(inputs: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (i in inputs) {
            val r = resolveStockCode(i)
            if (r != null) out.add(r)
        }
        return out.distinct()
    }

    /** 标记数据源降级 */
    fun markDataSourceFallback() {
        dataSourceFallback = true
    }

    val effectiveDataSource: String
        get() = if (dataSource == "ths" && dataSourceFallback) "tencent" else dataSource

    // ===== 行情刷新 =====
    fun refreshQuotes() {
        scope.launch {
            val rawWatch = watchlist.map { normalizeCode(it) }.distinct()
            val rawHold = holdings.map { normalizeCode(it.code) }.distinct()
            // 修复脏数据（名称被误存）
            val dirty = (rawWatch + rawHold).filter { !isCode(it) }.toMutableList()
            val dirtyResolved = mutableMapOf<String, String>()
            if (dirty.isNotEmpty()) {
                val it2 = dirty.iterator()
                while (it2.hasNext()) {
                    val d = it2.next()
                    val r = resolveStockCode(d)
                    if (r != null) dirtyResolved[d] = r else it2.remove()
                }
                if (rawWatch.any { !isCode(it) }) {
                    val newWatch = rawWatch.map { if (isCode(it)) it else dirtyResolved[it] }.filterNotNull().distinct()
                    store.setWatchlist(newWatch)
                    watchlist = newWatch
                }
                if (rawHold.any { !isCode(it) }) {
                    val newHold = holdings.map { h ->
                        val nc = normalizeCode(h.code)
                        if (isCode(nc)) h else h.copy(code = dirtyResolved[nc] ?: h.code)
                    }.filter { isCode(it.code) }
                    store.setCurrentAccountHoldings(newHold)
                    holdings = newHold
                }
            }
            val codes = (rawWatch.filter { isCode(it) } + rawHold.filter { isCode(it) }).distinct()
            if (codes.isEmpty()) {
                quotes = emptyMap()
                return@launch
            }
            loadingQuotes = true
            refreshError = null
            try {
                val useThs = dataSource == "ths" && thsApiKey.isNotBlank() && !dataSourceFallback
                val list = if (useThs) {
                    try {
                        val quotes = ThsApi.fetchQuotes(codes, thsApiKey)
                        val names = ThsApi.fetchNames(codes.filter { code -> quotes.any { it.code == code && it.name == code } }, thsApiKey)
                        quotes.map { q ->
                            if (q.name == q.code && names.containsKey(q.code)) q.copy(name = names[q.code] ?: q.name) else q
                        }
                    } catch (e: Exception) {
                        markDataSourceFallback()
                        refreshError = "同花顺API不可用，已自动切换到腾讯数据源"
                        MarketApi.fetchQuotes(codes)
                    }
                } else {
                    MarketApi.fetchQuotes(codes)
                }
                val newQuotes = list.associateBy { normalizeCode(it.code) }.toMutableMap()
                codes.forEach { c ->
                    if (!newQuotes.containsKey(c)) {
                        newQuotes[c] = Quote(code = c, name = c, price = 0.0, prevClose = 0.0,
                            open = 0.0, high = 0.0, low = 0.0, change = 0.0, changePct = 0.0,
                            volume = 0, amount = 0.0, turnover = 0.0, pe = 0.0, pb = 0.0,
                            marketCap = 0.0, floatCap = 0.0, amplitude = 0.0, time = "")
                    }
                }
                quotes = newQuotes
            } catch (e: Exception) {
                refreshError = e.message ?: "行情获取失败"
            } finally {
                loadingQuotes = false
            }
        }
    }

    // ===== 自选操作 =====
    fun addStocksAndRefresh(codes: List<String>) {
        scope.launch {
            val normalized = resolveStockCodes(codes)
            if (normalized.isEmpty()) return@launch
            store.addStocks(normalized)
            watchlist = (watchlist.map { normalizeCode(it) } + normalized).distinct()
            delay(200)
            refreshQuotes()
        }
    }

    fun addStockAndRefresh(code: String) = addStocksAndRefresh(listOf(code))

    fun removeStock(code: String) {
        scope.launch {
            store.removeStock(code)
            watchlist = watchlist.filterNot { normalizeCode(it) == normalizeCode(code) }
            quotes = quotes - normalizeCode(code)
        }
    }

    // ===== 持仓操作（多账号） =====
    fun addHoldingsAndRefresh(list: List<Holding>, source: String = "manual") {
        scope.launch {
            val normalized = mutableListOf<Holding>()
            for (h in list) {
                val code = resolveStockCode(h.code) ?: continue
                if (code == normalizeCode(h.code)) normalized.add(h) else normalized.add(h.copy(code = code))
            }
            if (normalized.isEmpty()) return@launch
            store.addHoldingsToCurrentAccount(normalized)
            holdings = store.getCurrentAccount().holdings
            delay(200)
            refreshQuotes()
            delay(300)
            store.saveHoldingSnapshot(HoldingSnapshot(
                holdings = holdings, source = source, accountId = currentHoldingAccountId,
            ))
            holdingSnapshots = store.getHoldingSnapshots()
        }
    }

    fun updateHolding(h: Holding) {
        scope.launch {
            store.updateHoldingInCurrentAccount(h)
            holdings = store.getCurrentAccount().holdings
        }
    }

    fun removeHolding(code: String) {
        scope.launch {
            store.removeHoldingFromCurrentAccount(code)
            holdings = store.getCurrentAccount().holdings
        }
    }

    /** 立即保存当前持仓快照（手动触发） */
    fun saveSnapshotNow() {
        scope.launch {
            store.saveHoldingSnapshot(HoldingSnapshot(
                holdings = holdings, source = "manual", accountId = currentHoldingAccountId,
            ))
            holdingSnapshots = store.getHoldingSnapshots()
        }
    }

    fun switchHoldingAccount(id: String) {
        scope.launch {
            store.setCurrentAccountId(id)
            currentHoldingAccountId = id
            holdings = store.getCurrentAccount().holdings
            refreshQuotes()
        }
    }

    fun renameHoldingAccount(id: String, name: String) {
        scope.launch {
            store.renameAccount(id, name)
            accounts = store.getAccounts()
        }
    }

    fun addHoldingAccount(name: String) {
        scope.launch {
            val id = store.addAccount(name)
            store.setCurrentAccountId(id)
            currentHoldingAccountId = id
            accounts = store.getAccounts()
            holdings = store.getCurrentAccount().holdings
        }
    }

    fun deleteHoldingAccount(id: String) {
        scope.launch {
            store.deleteAccount(id)
            accounts = store.getAccounts()
            currentHoldingAccountId = store.getCurrentAccountId()
            holdings = store.getCurrentAccount().holdings
        }
    }

    // ===== AI 配置 =====
    fun saveKeys(k1: String, k2: String) {
        store.setApiKeys(k1, k2)
        apiKey1 = k1
        apiKey2 = k2
    }

    fun saveModel(m: String) {
        store.setModel(m)
        model = m
    }

    fun saveTheme(t: String) {
        store.setTheme(t)
        theme = t
    }

    fun saveAiProvider(provider: String) {
        store.setAiProvider(provider)
        aiProvider = provider
        fetchedModels = emptyList()
    }

    fun saveProviderKey(provider: String, key: String) {
        store.setProviderKey(provider, key)
        providerKeys = store.getProviderKeys()
    }

    fun saveCustomBaseUrl(url: String) {
        store.setCustomBaseUrl(url)
        customBaseUrl = url
    }

    fun saveThsApiKey(key: String) {
        store.setThsApiKey(key)
        thsApiKey = key
    }

    fun saveDataSource(source: String) {
        store.setDataSource(source)
        dataSource = source
        dataSourceFallback = false
        refreshQuotes()
    }

    fun setAutoAnalysis(b: Boolean) {
        store.setAutoAnalysisEnabled(b)
        autoAnalysisEnabled = b
    }

    val currentApiKeys: List<String>
        get() {
            val p = aiProvider
            if (p == "siliconflow") return listOf(apiKey1, apiKey2)
            return listOf(providerKeys[p] ?: "")
        }

    // ===== 模型拉取 =====
    var fetchedModels by mutableStateOf<List<String>>(emptyList())
        private set
    var fetchingModels by mutableStateOf(false)
        private set
    var fetchModelsError by mutableStateOf<String?>(null)
        private set

    fun fetchModels() {
        val provider = aiProvider
        val key = if (provider == "siliconflow") apiKey1 else providerKeys[provider] ?: ""
        if (key.isBlank()) {
            fetchModelsError = "请先填写 API Key"
            return
        }
        fetchingModels = true
        fetchModelsError = null
        scope.launch {
            try {
                val models = AiApi.fetchModels(key, provider, customBaseUrl)
                fetchedModels = models
                if (models.isEmpty()) fetchModelsError = "未获取到模型列表"
            } catch (e: Exception) {
                fetchModelsError = e.message ?: "拉取失败"
            } finally {
                fetchingModels = false
            }
        }
    }

    // ===== AI 报告（个股） =====
    var aiReport by mutableStateOf<String?>(null)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set
    var aiLoading by mutableStateOf(false)
        private set

    fun generateAiReport(code: String, userRequest: String = "") {
        val q = quotes[normalizeCode(code)]
        val name = q?.name ?: code
        aiLoading = true
        aiError = null
        aiReport = null
        scope.launch {
            try {
                val kl = withContext(Dispatchers.IO) { MarketApi.fetchKline(code, "day", 320) }
                val ind = com.dsa.app.analysis.Indicators.computeAll(kl)
                val messages = AiApi.buildAnalysisMessages(q, kl, ind, userRequest)
                val content = AiApi.chat(currentApiKeys, model, messages, aiProvider, customBaseUrl)
                aiReport = content
                val report = AnalysisReport(code = normalizeCode(code), name = name, content = content, model = model)
                store.saveAnalysisReport(report)
                analysisReports = store.getAnalysisReports()
            } catch (e: Exception) {
                aiError = e.message ?: "AI 分析失败"
            } finally {
                aiLoading = false
            }
        }
    }

    // ===== 组合分析 =====
    var portfolioReport by mutableStateOf<String?>(null)
    var portfolioError by mutableStateOf<String?>(null)
        private set
    var portfolioLoading by mutableStateOf(false)
        private set

    fun runPortfolioAnalysisAndSave(accountId: String = currentHoldingAccountId) {
        portfolioLoading = true
        portfolioError = null
        portfolioReport = null
        scope.launch {
            try {
                val acc = accounts.firstOrNull { it.id == accountId } ?: store.getCurrentAccount()
                val items = mutableListOf<AiApi.PortfolioItem>()
                val codes = acc.holdings.map { normalizeCode(it.code) }.filter { isCode(it) }
                if (codes.isEmpty()) {
                    portfolioError = "该账号暂无持仓"
                    return@launch
                }
                for (c in codes) {
                    val q = quotes[c]
                    val h = acc.holdings.firstOrNull { normalizeCode(it.code) == c }
                    val kl = withContext(Dispatchers.IO) { MarketApi.fetchKline(c, "day", 320) }
                    val ind = com.dsa.app.analysis.Indicators.computeAll(kl)
                    val summary = com.dsa.app.analysis.Indicators.summarize(kl, ind)
                    items.add(AiApi.PortfolioItem(
                        code = c,
                        name = q?.name ?: h?.code ?: c,
                        price = q?.price ?: 0.0,
                        changePct = q?.changePct ?: 0.0,
                        summary = summary,
                        shares = h?.shares ?: 0,
                        costPrice = h?.costPrice ?: 0.0,
                    ))
                }
                val messages = AiApi.buildPortfolioAnalysis(items, "持仓组合（${acc.name}）")
                val content = AiApi.chat(currentApiKeys, model, messages, aiProvider, customBaseUrl)
                portfolioReport = content
                store.savePortfolioReport(PortfolioReport(
                    content = content, model = model, stockCount = items.size,
                    totalValue = items.sumOf { it.price * it.shares },
                    accountId = acc.id, accountName = acc.name,
                ))
                portfolioReports = store.getPortfolioReports()
            } catch (e: Exception) {
                portfolioError = e.message ?: "组合分析失败"
            } finally {
                portfolioLoading = false
            }
        }
    }

    // ===== 持仓变化分析 =====
    fun analyzeHoldingChange(oldSnapshot: HoldingSnapshot, newSnapshot: HoldingSnapshot) {
        portfolioLoading = true
        portfolioError = null
        portfolioReport = null
        scope.launch {
            try {
                val sb = StringBuilder()
                sb.append("以下是同一持仓账号两次快照的对比，请逐股分析持仓变化并给出后续操作建议。\n\n")
                sb.append("【旧快照】${formatTime(oldSnapshot.createdAt)}（${oldSnapshot.source}）共 ${oldSnapshot.holdings.size} 只\n")
                sb.append("【新快照】${formatTime(newSnapshot.createdAt)}（${newSnapshot.source}）共 ${newSnapshot.holdings.size} 只\n\n")
                val oldMap = oldSnapshot.holdings.associateBy { normalizeCode(it.code) }
                val newMap = newSnapshot.holdings.associateBy { normalizeCode(it.code) }
                val allCodes = (oldMap.keys + newMap.keys).filter { isCode(it) }
                allCodes.forEach { c ->
                    val o = oldMap[c]
                    val n = newMap[c]
                    val q = quotes[c]
                    val name = q?.name ?: c
                    sb.append("### $name（$c）\n")
                    when {
                        o == null && n != null -> sb.append("新增持仓：${n.shares}股，成本${n.costPrice}，现价${q?.price ?: "—"}（浮盈${calcProfit(n, q)}）\n")
                        o != null && n == null -> sb.append("已清仓：原持仓${o.shares}股，成本${o.costPrice}\n")
                        else -> {
                            val diff = (n?.shares ?: 0) - (o?.shares ?: 0)
                            val costDiff = (n?.costPrice ?: 0.0) - (o?.costPrice ?: 0.0)
                            if (diff > 0) sb.append("增持 ${diff}股（${o?.shares}→${n?.shares}），成本${o?.costPrice}→${n?.costPrice}（${if (costDiff >= 0) "+" else ""}${"%.2f".format(costDiff)}）\n")
                            else if (diff < 0) sb.append("减持 ${-diff}股（${o?.shares}→${n?.shares}）\n")
                            else sb.append("持仓数量未变（${o?.shares}股）\n")
                        }
                    }
                    if (n != null) {
                        sb.append("成本${n.costPrice}，现价${q?.price ?: "—"}，浮盈${calcProfit(n, q)}\n")
                    }
                    sb.append("操作建议：结合行情与变化方向给出明确建议（继续持有/加仓/减仓/止盈止损）。\n\n")
                }
                sb.append("请输出 Markdown 报告：## 总体变化 ## 个股逐笔分析（含操作建议） ## 风险提示")
                val messages = listOf(
                    ChatMessage("system", "你是资深A股投资顾问，擅长持仓变化分析与操作建议。输出严谨、数字准确、风险意识强，不编造数据。"),
                    ChatMessage("user", sb.toString()),
                )
                val content = AiApi.chat(currentApiKeys, model, messages, aiProvider, customBaseUrl)
                portfolioReport = content
                store.saveHoldingChangeReport(HoldingChangeReport(
                    content = content, model = model,
                    oldSnapshotId = oldSnapshot.id, newSnapshotId = newSnapshot.id,
                    oldSnapshotTime = oldSnapshot.createdAt, newSnapshotTime = newSnapshot.createdAt,
                    accountId = newSnapshot.accountId,
                    accountName = accounts.firstOrNull { it.id == newSnapshot.accountId }?.name ?: "账号",
                ))
                holdingChangeReports = store.getHoldingChangeReports()
            } catch (e: Exception) {
                portfolioError = e.message ?: "变化分析失败"
            } finally {
                portfolioLoading = false
            }
        }
    }

    private fun calcProfit(h: Holding, q: Quote?): String {
        if (h.costPrice <= 0 || q == null) return "—"
        val p = (q.price - h.costPrice) / h.costPrice * 100
        return "%+.2f%%".format(p)
    }

    private fun formatTime(ms: Long): String {
        val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        return "%04d-%02d-%02d %02d:%02d".format(dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute)
    }
}
