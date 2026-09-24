package com.dsa.app.data

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.header
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * 行情数据层（KMP 版，Ktor 客户端）
 * - 实时行情：腾讯 qt.gtimg.cn（GBK）
 * - K线历史：新浪 quotes.sina.cn（JSONP）
 * - 股票搜索：腾讯 smartbox.gtimg.cn（GBK）
 */
object MarketApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { createHttpClient() }

    private const val UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile Safari/604.1"

    /** 股票代码 → 腾讯前缀代码 */
    fun toTencentCode(code: String): String {
        var c = code.trim().lowercase()
        c = c.removeSuffix(".sh").removeSuffix(".sz").removeSuffix(".bj")
        c = c.removePrefix("sh").removePrefix("sz").removePrefix("bj")
        return when {
            c.startsWith("6") || c.startsWith("9") || c.startsWith("5") -> "sh$c"
            c.startsWith("0") || c.startsWith("3") || c.startsWith("1") || c.startsWith("2") -> "sz$c"
            c.startsWith("4") || c.startsWith("8") -> "bj$c"
            else -> "sh$c"
        }
    }

    /** 批量获取实时行情 */
    suspend fun fetchQuotes(codes: List<String>): List<Quote> = withContext(Dispatchers.IO) {
        if (codes.isEmpty()) return@withContext emptyList()
        val params = codes.joinToString(",") { toTencentCode(it) }
        val url = "https://qt.gtimg.cn/q=$params"
        val resp = client.get(url) {
            header("User-Agent", UA)
            header("Referer", "https://finance.qq.com/")
        }
        val bytes = resp.readBytes()
        val text = decodeGbk(bytes)
        text.lineSequence().mapNotNull { parseQuoteLine(it) }.toList()
    }

    /** 获取单个实时行情 */
    suspend fun fetchQuote(code: String): Quote? = fetchQuotes(listOf(code)).firstOrNull()

    /** 解析腾讯行情单行 */
    fun parseQuoteLine(line: String): Quote? {
        val eq = line.indexOf('=')
        if (eq < 0) return null
        val raw = line.substring(eq + 1).trim()
        if (raw.length < 3) return null
        val body = raw.substring(1, raw.length - 1)
        val f = body.split("~")
        if (f.size < 48) return null
        fun d(idx: Int): Double = f.getOrNull(idx)?.trim()?.toDoubleOrNull() ?: 0.0
        fun l(idx: Int): Long = f.getOrNull(idx)?.trim()?.toLongOrNull() ?: 0L
        val code = f.getOrNull(2)?.trim() ?: return null
        val name = f.getOrNull(1)?.trim() ?: code
        return Quote(
            code = code, name = name,
            price = d(3), prevClose = d(4), open = d(5),
            high = d(33), low = d(34),
            change = d(31), changePct = d(32),
            volume = l(36), amount = d(37), turnover = d(38),
            pe = d(39), pb = d(46),
            marketCap = d(45), floatCap = d(44),
            amplitude = d(43), time = f.getOrNull(30)?.trim() ?: "",
        )
    }

    /** 获取 K 线（新浪） */
    suspend fun fetchKline(code: String, period: String, datalen: Int = 320): List<KlinePoint> = withContext(Dispatchers.IO) {
        val tc = toTencentCode(code)
        val scale = sinaScale(period)
        val url = "https://quotes.sina.cn/cn/api/jsonp_v2.php/var%20_=/CN_MarketDataService.getKLineData?symbol=$tc&scale=$scale&ma=no&datalen=$datalen"
        val resp = client.get(url) { header("User-Agent", UA) }
        val text = resp.bodyAsText()
        parseSinaKline(text)
    }

    /** 新浪 K 线周期码 */
    fun sinaScale(period: String): Int = when (period) {
        "week" -> 1200
        "month" -> 7200
        else -> 240
    }

    /** 解析新浪 K 线 JSONP */
    fun parseSinaKline(text: String): List<KlinePoint> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val body = text.substring(start, end + 1)
        val list = try {
            json.decodeFromString<List<SinaKline>>(body)
        } catch (e: Exception) {
            return emptyList()
        }
        return list.map { k ->
            KlinePoint(day = k.day, open = k.openV, high = k.highV, low = k.lowV, close = k.closeV, volume = k.volumeV)
        }
    }

    /** 股票搜索（腾讯 smartbox） */
    suspend fun searchStocks(keyword: String): List<StockSuggestion> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val url = "https://smartbox.gtimg.cn/s3/?q=${encodeUrl(keyword)}&t=all"
        val resp = client.get(url) { header("User-Agent", UA) }
        val bytes = resp.readBytes()
        val text = decodeGbk(bytes)
        parseSmartbox(text)
    }

    fun parseSmartbox(text: String): List<StockSuggestion> {
        val start = text.indexOf('"')
        val end = text.lastIndexOf('"')
        if (start < 0 || end <= start) return emptyList()
        val body = text.substring(start + 1, end)
        return body.split("^").mapNotNull { item ->
            val f = item.split("~")
            if (f.size < 5) return@mapNotNull null
            val rawType = f.getOrNull(4)?.uppercase() ?: ""
            val type = when {
                rawType.startsWith("GP") -> "gp"
                rawType.startsWith("ZS") -> "zs"
                else -> return@mapNotNull null
            }
            StockSuggestion(
                code = f.getOrNull(1) ?: return@mapNotNull null,
                name = f.getOrNull(2) ?: "",
                market = f.getOrNull(0) ?: "sh",
                type = type,
            )
        }.distinctBy { it.market + it.code }.take(20)
    }

    private fun encodeUrl(s: String): String =
        s.encodeURLQueryComponent()
}
