package com.dsa.app.analysis

import com.dsa.app.data.IndicatorResult
import com.dsa.app.data.KlinePoint
import com.dsa.app.data.Quote

/**
 * 本地确定性技术信号规则引擎。
 * 借鉴「归类专家」matchByRule 的思路：先用可解释的本地规则产出确定性信号，
 * 再交由 AI 做综合研判 —— 规则先行、AI 兜底，提高分析的准确性与可解释性。
 */
object SignalRules {

    enum class Level { BULLISH, BEARISH, NEUTRAL }

    data class Signal(
        val name: String,
        val level: Level,
        val text: String,
        val source: String, // 哪个指标/规则触发
    )

    fun analyze(ind: IndicatorResult, quote: Quote?, kline: List<KlinePoint>): List<Signal> {
        val out = mutableListOf<Signal>()
        val n = ind.ma5?.size ?: ind.dif?.size ?: kline.size
        val last = n - 1
        if (last < 1) return out

        fun arr(a: DoubleArray?): Double? = a?.getOrNull(last)
        fun prev(a: DoubleArray?): Double? = a?.getOrNull(last - 1)
        fun avg(arr: DoubleArray?, from: Int, to: Int): Double? {
            val a = arr ?: return null
            if (from < 0 || to >= a.size || from > to) return null
            var s = 0.0
            for (i in from..to) s += a[i]
            return s / (to - from + 1)
        }

        val price = quote?.price
        val ma5 = arr(ind.ma5); val ma10 = arr(ind.ma10); val ma20 = arr(ind.ma20); val ma60 = arr(ind.ma60)
        val dif = arr(ind.dif); val dea = arr(ind.dea)
        val pdif = prev(ind.dif); val pdea = prev(ind.dea)
        val k = arr(ind.k); val d = arr(ind.d); val j = arr(ind.j)
        val pk = prev(ind.k); val pd = prev(ind.d)
        val rsi6 = arr(ind.rsi6); val rsi12 = arr(ind.rsi12)
        val bollUp = arr(ind.bollUp); val bollLow = arr(ind.bollLow); val bollMid = arr(ind.bollMid)
        val wr = arr(ind.wr); val cci = arr(ind.cci)
        val adx = arr(ind.adx); val pdi = arr(ind.pdi); val mdi = arr(ind.mdi)
        val bias6 = arr(ind.bias6)
        val sar = arr(ind.sar)
        val roc = arr(ind.roc)

        // 1. MA 多头/空头排列
        if (ma5 != null && ma10 != null && ma20 != null) {
            if (ma5 > ma10 && ma10 > ma20) out += Signal("均线多头排列", Level.BULLISH, "MA5(${f(ma5)})>MA10(${f(ma10)})>MA20(${f(ma20)})，短期均线呈多头排列，趋势向上", "MA")
            else if (ma5 < ma10 && ma10 < ma20) out += Signal("均线空头排列", Level.BEARISH, "MA5(${f(ma5)})<MA10(${f(ma10)})<MA20(${f(ma20)})，短期均线呈空头排列，趋势向下", "MA")
        }
        // 2. MA 金叉/死叉
        if (ma5 != null && ma20 != null && prev(ind.ma5) != null && prev(ind.ma20) != null) {
            val p5 = prev(ind.ma5)!!; val p20 = prev(ind.ma20)!!
            if (p5 <= p20 && ma5 > ma20) out += Signal("MA5 上穿 MA20（金叉）", Level.BULLISH, "MA5 由下向上穿越 MA20，短线转强", "MA")
            if (p5 >= p20 && ma5 < ma20) out += Signal("MA5 下穿 MA20（死叉）", Level.BEARISH, "MA5 由上向下穿越 MA20，短线转弱", "MA")
        }
        // 3. 价格与 MA20
        if (price != null && ma20 != null) {
            if (price > ma20) out += Signal("站上20日均线", Level.BULLISH, "现价 ${f(price)} 位于 MA20(${f(ma20)}) 之上", "MA")
            else out += Signal("跌破20日均线", Level.BEARISH, "现价 ${f(price)} 位于 MA20(${f(ma20)}) 之下", "MA")
        }
        // 4. MACD
        if (dif != null && dea != null) {
            if (dif > dea && dif > 0) out += Signal("MACD 多头", Level.BULLISH, "DIF(${f(dif)})>DEA(${f(dea)})>0，多头动能占优", "MACD")
            else if (dif < dea && dif < 0) out += Signal("MACD 空头", Level.BEARISH, "DIF(${f(dif)})<DEA(${f(dea)})<0，空头动能占优", "MACD")
            if (pdif != null && pdea != null && pdif <= pdea && dif > dea) out += Signal("MACD 金叉", Level.BULLISH, "DIF 上穿 DEA，动能转强", "MACD")
            if (pdif != null && pdea != null && pdif >= pdea && dif < dea) out += Signal("MACD 死叉", Level.BEARISH, "DIF 下穿 DEA，动能转弱", "MACD")
        }
        // 5. KDJ
        if (k != null && d != null) {
            if (pk != null && pd != null && pk <= pd && k > d) out += Signal("KDJ 金叉", Level.BULLISH, "K(${f(k)}) 上穿 D(${f(d)})，短线转强", "KDJ")
            if (pk != null && pd != null && pk >= pd && k < d) out += Signal("KDJ 死叉", Level.BEARISH, "K(${f(k)}) 下穿 D(${f(d)})，短线转弱", "KDJ")
            if (j != null && j > 90) out += Signal("KDJ 超买", Level.BEARISH, "J 值 ${f(j)}>90，短线超买，注意回调风险", "KDJ")
            if (j != null && j < 10) out += Signal("KDJ 超卖", Level.BULLISH, "J 值 ${f(j)}<10，短线超卖，存在反弹机会", "KDJ")
        }
        // 6. RSI
        if (rsi6 != null) {
            if (rsi6 > 80) out += Signal("RSI 超买", Level.BEARISH, "RSI6 ${f(rsi6)}>80，超买区域，警惕回调", "RSI")
            if (rsi6 < 20) out += Signal("RSI 超卖", Level.BULLISH, "RSI6 ${f(rsi6)}<20，超卖区域，存在修复机会", "RSI")
            if (rsi12 != null && rsi6 > rsi12) out += Signal("RSI 走强", Level.BULLISH, "RSI6(${f(rsi6)})>RSI12(${f(rsi12)})，短线动能增强", "RSI")
        }
        // 7. BOLL
        if (price != null && bollUp != null && bollLow != null && bollMid != null) {
            if (price >= bollUp) out += Signal("突破布林上轨", Level.BULLISH, "现价 ${f(price)} 触及上轨 ${f(bollUp)}，强势但短线超买", "BOLL")
            if (price <= bollLow) out += Signal("跌破布林下轨", Level.BEARISH, "现价 ${f(price)} 触及下轨 ${f(bollLow)}，弱势但短线超卖", "BOLL")
        }
        // 8. WR / CCI / ADX / BIAS / ROC / SAR
        if (wr != null) {
            if (wr < 20) out += Signal("WR 超买", Level.BEARISH, "威廉指标 ${f(wr)}<20，超买", "WR")
            if (wr > 80) out += Signal("WR 超卖", Level.BULLISH, "威廉指标 ${f(wr)}>80，超卖", "WR")
        }
        if (cci != null) {
            if (cci > 100) out += Signal("CCI 强势", Level.BULLISH, "CCI ${f(cci)}>100，强势区", "CCI")
            if (cci < -100) out += Signal("CCI 超卖", Level.BULLISH, "CCI ${f(cci)}<-100，超卖区", "CCI")
        }
        if (adx != null) {
            if (adx > 25) out += Signal("趋势强度高", Level.NEUTRAL, "ADX ${f(adx)}>25，趋势明确", "DMI")
            if (pdi != null && mdi != null) {
                if (pdi > mdi) out += Signal("多方主导", Level.BULLISH, "+DI(${f(pdi)})>-DI(${f(mdi)})，多方主导", "DMI")
                else out += Signal("空方主导", Level.BEARISH, "-DI(${f(mdi)})>+DI(${f(pdi)})，空方主导", "DMI")
            }
        }
        if (bias6 != null) {
            if (bias6 > 10) out += Signal("BIAS 超买", Level.BEARISH, "乖离率 ${f(bias6)}>10%，短线偏离过大", "BIAS")
            if (bias6 < -10) out += Signal("BIAS 超卖", Level.BULLISH, "乖离率 ${f(bias6)}<-10%，短线超跌", "BIAS")
        }
        if (roc != null) {
            if (roc > 0) out += Signal("ROC 转正", Level.BULLISH, "ROC ${f(roc)}>0，动量向上", "ROC")
            if (roc < 0) out += Signal("ROC 转负", Level.BEARISH, "ROC ${f(roc)}<0，动量向下", "ROC")
        }
        if (price != null && sar != null) {
            if (price > sar) out += Signal("SAR 多头", Level.BULLISH, "现价 ${f(price)}>SAR(${f(sar)})，趋势指标偏多", "SAR")
            else out += Signal("SAR 空头", Level.BEARISH, "现价 ${f(price)}<SAR(${f(sar)})，趋势指标偏空", "SAR")
        }
        // 9. 量价（需 kline 有成交量）
        if (kline.size >= 6 && kline.last().volume > 0) {
            val vols = kline.takeLast(6).map { it.volume }
            val avg5 = vols.take(5).average()
            val cur = vols.last()
            val changePct = if (kline.size >= 2 && kline[kline.size - 2].close > 0)
                (kline.last().close - kline[kline.size - 2].close) / kline[kline.size - 2].close * 100 else 0.0
            if (cur > avg5 * 1.8 && changePct > 2) out += Signal("放量上涨", Level.BULLISH, "今日量能 ${fmtVol(cur)} 为5日均量 ${fmtVol(avg5)} 的 ${f(cur / avg5)} 倍且上涨 ${f(changePct)}%，资金明显介入", "量价")
            if (cur > avg5 * 1.8 && changePct < -2) out += Signal("放量下跌", Level.BEARISH, "今日量能放大 ${f(cur / avg5)} 倍且下跌 ${f(changePct)}%，抛压明显", "量价")
        }
        // 10. 涨跌停
        if (quote != null) {
            if (quote.isLimitUp) out += Signal("涨停", Level.BULLISH, "今日涨停，情绪极强，注意次日分歧风险", "行情")
            if (quote.price <= quote.limitDown) out += Signal("跌停", Level.BEARISH, "今日跌停，情绪极弱", "行情")
        }
        return out
    }

    /** 汇总为一段可读文本（注入 AI prompt 用） */
    fun summarize(signals: List<Signal>): String {
        if (signals.isEmpty()) return "（暂无明确技术信号）"
        val bullish = signals.count { it.level == Level.BULLISH }
        val bearish = signals.count { it.level == Level.BEARISH }
        val sb = StringBuilder()
        sb.append("技术信号 ${signals.size} 条（看多 $bullish / 看空 $bearish）：\n")
        signals.forEach { sb.append("- [${it.name}] ${it.text}\n") }
        return sb.toString()
    }

    private fun f(v: Double): String = com.dsa.app.util.Fmt.d(v)
    private fun fmtVol(v: Double): String = if (v >= 10000) "${com.dsa.app.util.Fmt.d(v / 10000)}万手" else "${com.dsa.app.util.Fmt.d(v)}手"
}
