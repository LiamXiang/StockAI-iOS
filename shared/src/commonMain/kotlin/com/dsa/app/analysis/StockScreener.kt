package com.dsa.app.analysis

import com.dsa.app.data.IndicatorResult
import com.dsa.app.data.KlinePoint
import kotlin.math.abs

/**
 * 智能选股：基于技术指标条件筛选股票
 * 在自选股/持仓股范围内进行多策略筛选
 */
object StockScreener {

    /** 选股策略定义 */
    data class Strategy(
        val id: String,
        val name: String,
        val desc: String,
        val minBars: Int = 30,
    )

    /** 预设选股策略列表 */
    val STRATEGIES = listOf(
        Strategy("macd_gold", "MACD金叉", "DIF上穿DEA，多头动能增强", 30),
        Strategy("kdj_gold", "KDJ低位金叉", "K上穿D且K<50，超卖反弹信号", 15),
        Strategy("ma_bull", "均线多头排列", "MA5>MA10>MA20，趋势向上", 25),
        Strategy("oversold", "超跌反弹", "RSI6<30或KDJ<20，短期超卖", 15),
        Strategy("boll_break", "突破布林上轨", "收盘价突破布林上轨，强势突破", 25),
        Strategy("volume_up", "放量上涨", "成交量>5日均量1.5倍且收涨", 10),
        Strategy("sar_buy", "SAR买入信号", "SAR从空头转多头，止损位上移", 10),
        Strategy("cci_rebound", "CCI超卖反弹", "CCI从-100下方上穿-100", 20),
        Strategy("bias_low", "负乖离过大", "BIAS6<-5，股价远离均线有反弹需求", 10),
        Strategy("adx_trend", "ADX趋势增强", "ADX>25且+DI>-DI，上升趋势加强", 30),
    )

    /** 单只股票的选股结果 */
    data class ScreenResult(
        val code: String,
        val name: String,
        val price: Double,
        val changePct: Double,
        val matchedStrategies: List<String>,  // 匹配的策略ID列表
        val score: Int,  // 综合评分（匹配策略数加权）
    )

    /**
     * 对单只股票执行所有策略筛选
     * @return 匹配的策略ID列表
     */
    fun screenOne(kline: List<KlinePoint>, ind: IndicatorResult): List<String> {
        if (kline.size < 10) return emptyList()
        val matched = mutableListOf<String>()
        val n = kline.size - 1
        val closes = kline.map { it.close }.toDoubleArray()
        val volumes = kline.map { it.volume }.toDoubleArray()

        // 1. MACD金叉：DIF上穿DEA（前一天DIF<=DEA，今天DIF>DEA）
        if (n >= 1 && ind.dif != null && ind.dea != null) {
            val dif = ind.dif
            val dea = ind.dea
            if (n >= 1 && !dif[n].isNaN() && !dea[n].isNaN() && !dif[n-1].isNaN() && !dea[n-1].isNaN()) {
                if (dif[n-1] <= dea[n-1] && dif[n] > dea[n]) matched.add("macd_gold")
            }
        }

        // 2. KDJ低位金叉：K上穿D且K<50
        if (n >= 1 && ind.k != null && ind.d != null) {
            val k = ind.k
            val d = ind.d
            if (!k[n].isNaN() && !d[n].isNaN() && !k[n-1].isNaN() && !d[n-1].isNaN()) {
                if (k[n-1] <= d[n-1] && k[n] > d[n] && k[n] < 50) matched.add("kdj_gold")
            }
        }

        // 3. 均线多头排列：MA5>MA10>MA20
        if (ind.ma5 != null && ind.ma10 != null && ind.ma20 != null) {
            val m5 = ind.ma5[n]
            val m10 = ind.ma10[n]
            val m20 = ind.ma20[n]
            if (!m5.isNaN() && !m10.isNaN() && !m20.isNaN() && m5 > m10 && m10 > m20) {
                matched.add("ma_bull")
            }
        }

        // 4. 超跌反弹：RSI6<30 或 KDJ K<20
        if (ind.rsi6 != null && !ind.rsi6[n].isNaN() && ind.rsi6[n] < 30) {
            matched.add("oversold")
        } else if (ind.k != null && !ind.k[n].isNaN() && ind.k[n] < 20) {
            matched.add("oversold")
        }

        // 5. 突破布林上轨：收盘价>布林上轨
        if (ind.bollUp != null && !ind.bollUp[n].isNaN() && closes[n] > ind.bollUp[n]) {
            matched.add("boll_break")
        }

        // 6. 放量上涨：成交量>5日均量1.5倍，且收涨
        if (n >= 5) {
            val vol5 = volumes.takeLast(5).dropLast(1).average()  // 前5日均量（不含今天）
            val todayVol = volumes[n]
            val isUp = closes[n] > closes[n-1]
            if (vol5 > 0 && todayVol > vol5 * 1.5 && isUp) matched.add("volume_up")
        }

        // 7. SAR买入信号：SAR从空头转多头（前一天SAR>收盘价，今天SAR<收盘价）
        if (n >= 1 && ind.sar != null) {
            val sar = ind.sar
            if (!sar[n].isNaN() && !sar[n-1].isNaN()) {
                if (sar[n-1] > closes[n-1] && sar[n] < closes[n]) matched.add("sar_buy")
            }
        }

        // 8. CCI超卖反弹：CCI从-100下方上穿-100
        if (n >= 1 && ind.cci != null) {
            val cci = ind.cci
            if (!cci[n].isNaN() && !cci[n-1].isNaN()) {
                if (cci[n-1] < -100 && cci[n] >= -100) matched.add("cci_rebound")
            }
        }

        // 9. 负乖离过大：BIAS6<-5
        if (ind.bias6 != null && !ind.bias6[n].isNaN() && ind.bias6[n] < -5) {
            matched.add("bias_low")
        }

        // 10. ADX趋势增强：ADX>25且+DI>-DI
        if (ind.adx != null && ind.pdi != null && ind.mdi != null) {
            val adx = ind.adx[n]
            val pdi = ind.pdi[n]
            val mdi = ind.mdi[n]
            if (!adx.isNaN() && !pdi.isNaN() && !mdi.isNaN() && adx > 25 && pdi > mdi) {
                matched.add("adx_trend")
            }
        }

        return matched
    }

    /** 计算综合评分：每个匹配策略加权 */
    fun calcScore(matched: List<String>): Int {
        var score = 0
        matched.forEach { id ->
            score += when (id) {
                "macd_gold" -> 15
                "kdj_gold" -> 12
                "ma_bull" -> 18
                "oversold" -> 10
                "boll_break" -> 14
                "volume_up" -> 12
                "sar_buy" -> 13
                "cci_rebound" -> 10
                "bias_low" -> 8
                "adx_trend" -> 16
                else -> 5
            }
        }
        return score
    }

    /** 获取策略名称 */
    fun strategyName(id: String): String = STRATEGIES.find { it.id == id }?.name ?: id
}
