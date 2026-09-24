package com.dsa.app.data

import com.dsa.app.util.Fmt

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.header
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 同花顺金融数据 API（KMP 版，Ktor 客户端）
 * Base URL: https://fuyao.aicubes.cn
 * 认证: X-api-key 请求头
 */
object ThsApi {
    private const val BASE_URL = "https://fuyao.aicubes.cn"
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { createHttpClient() }

    /** 代码转 thscode */
    fun toThsCode(code: String): String {
        var c = code.trim().lowercase()
        c = c.removeSuffix(".sh").removeSuffix(".sz").removeSuffix(".bj")
        c = c.removePrefix("sh").removePrefix("sz").removePrefix("bj")
        return when {
            c.startsWith("6") || c.startsWith("9") || c.startsWith("5") -> "$c.SH"
            c.startsWith("0") || c.startsWith("3") || c.startsWith("1") || c.startsWith("2") -> "$c.SZ"
            c.startsWith("4") || c.startsWith("8") -> "$c.BJ"
            else -> "$c.SH"
        }
    }

    /** thscode 转纯代码 */
    fun fromThsCode(thscode: String): String = thscode.substringBefore(".")

    private suspend fun get(path: String, apiKey: String): String {
        val resp = client.get("$BASE_URL$path") {
            header("X-api-key", apiKey)
        }
        if (resp.status.value != 200) throw Exception("同花顺 HTTP ${resp.status.value}")
        return resp.bodyAsText()
    }

    /** 批量获取行情快照 */
    suspend fun fetchQuotes(codes: List<String>, apiKey: String): List<Quote> {
        if (codes.isEmpty()) return emptyList()
        val thscodes = codes.joinToString(",") { toThsCode(it) }
        val response = get("/api/a-share/prices/snapshot?thscodes=${thscodes.encodeURLQueryComponent()}", apiKey)
        val root = json.parseToJsonElement(response).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "0") {
            throw Exception("同花顺API错误: ${root["message"]?.jsonPrimitive?.content}")
        }
        val items = root["data"]?.jsonObject?.get("item")?.jsonArray ?: return emptyList()
        return items.map { item ->
            val obj = item.jsonObject
            val thscode = obj["thscode"]?.jsonPrimitive?.content ?: ""
            val code = fromThsCode(thscode)
            val lastPrice = obj["last_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val prevPrice = obj["prev_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val priceChange = obj["price_change"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val changePct = obj["price_change_ratio_pct"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val high = obj["high_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val low = obj["low_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val open = obj["open_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val volume = obj["volume"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L
            val turnover = obj["turnover"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val amplitude = if (prevPrice > 0) (high - low) / prevPrice * 100 else 0.0
            Quote(
                code = code, name = code, price = lastPrice, prevClose = prevPrice,
                open = open, high = high, low = low, change = priceChange, changePct = changePct,
                volume = volume, amount = turnover / 10000.0, turnover = 0.0,
                pe = 0.0, pb = 0.0, marketCap = 0.0, floatCap = 0.0, amplitude = amplitude, time = "",
            )
        }
    }

    /** 获取历史K线 */
    suspend fun fetchKline(code: String, days: Int = 250, apiKey: String): List<KlinePoint> {
        val end = System.currentTimeMillis()
        val start = end - (days.toLong() * 86400000)
        val thscode = toThsCode(code)
        val response = get(
            "/api/a-share/prices/historical?thscode=$thscode&interval=1d&start=$start&end=$end&adjust=forward",
            apiKey
        )
        val root = json.parseToJsonElement(response).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "0") {
            throw Exception("同花顺API错误: ${root["message"]?.jsonPrimitive?.content}")
        }
        val items = root["data"]?.jsonObject?.get("item")?.jsonArray ?: return emptyList()
        return items.map { item ->
            val obj = item.jsonObject
            val dateMs = obj["date_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            KlinePoint(
                day = formatDate(dateMs),
                open = obj["open_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                high = obj["high_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                low = obj["low_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                close = obj["close_price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                volume = obj["volume"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            )
        }.sortedBy { it.day }
    }

    private fun formatDate(ms: Long): String {
        // yyyy-MM-dd（跨平台，使用 kotlinx-datetime）
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
        val dt = instant.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        return Fmt.ymd(dt.year,dt.monthNumber,dt.dayOfMonth)
    }

    /** 搜索股票 */
    suspend fun search(keyword: String, apiKey: String, limit: Int = 20): List<StockSuggestion> {
        val encoded = keyword.encodeURLQueryComponent()
        val response = get("/api/meta/tickers/search?q=$encoded&asset_type=a-share&limit=$limit", apiKey)
        val root = json.parseToJsonElement(response).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "0") {
            throw Exception("同花顺API错误: ${root["message"]?.jsonPrimitive?.content}")
        }
        val items = root["data"]?.jsonObject?.get("item")?.jsonArray ?: return emptyList()
        return items.map { item ->
            val obj = item.jsonObject
            val thscode = obj["thscode"]?.jsonPrimitive?.content ?: ""
            val exchange = obj["exchange"]?.jsonPrimitive?.content ?: "SH"
            StockSuggestion(
                code = fromThsCode(thscode),
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                market = exchange.lowercase(),
                type = "GP-A股票",
            )
        }
    }

    /** 获取股票名称（批量） */
    suspend fun fetchNames(codes: List<String>, apiKey: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        codes.forEach { code ->
            try {
                val searchResults = search(code, apiKey, 1)
                if (searchResults.isNotEmpty()) {
                    result[code] = searchResults[0].name
                }
            } catch (e: Exception) {
                // 忽略单个失败
            }
        }
        return result
    }
}
