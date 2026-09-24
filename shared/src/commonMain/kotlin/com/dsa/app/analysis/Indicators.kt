package com.dsa.app.analysis

import com.dsa.app.data.IndicatorResult
import com.dsa.app.data.KlinePoint
import kotlin.math.abs

/**
 * 技术指标计算：MA / EMA / MACD / KDJ / RSI / BOLL
 * 纯 Kotlin 实现，输入 K 线序列，输出与输入等长的数组（前段为 NaN）。
 */
object Indicators {

    /** 简单移动平均 */
    fun ma(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return out
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    /** 指数移动平均（平滑系数 2/(n+1)，首值取第一个数据） */
    fun ema(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.isEmpty()) return out
        val k = 2.0 / (period + 1)
        out[0] = values[0]
        for (i in 1 until values.size) {
            out[i] = values[i] * k + out[i - 1] * (1 - k)
        }
        return out
    }

    /** MACD(12,26,9)：DIF/DEA/柱 */
    fun macd(closes: DoubleArray, fast: Int = 12, slow: Int = 26, signal: Int = 9): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val dif = DoubleArray(closes.size) { i -> emaFast[i] - emaSlow[i] }
        val dea = ema(dif, signal)
        val macd = DoubleArray(closes.size) { i -> (dif[i] - dea[i]) * 2.0 }
        return Triple(dif, dea, macd)
    }

    /** KDJ(9,3,3) */
    fun kdj(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, n: Int = 9): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val size = closes.size
        val k = DoubleArray(size) { 50.0 }
        val d = DoubleArray(size) { 50.0 }
        val j = DoubleArray(size) { 50.0 }
        for (i in 0 until size) {
            val start = (i - n + 1).coerceAtLeast(0)
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (t in start..i) {
                if (highs[t] > hh) hh = highs[t]
                if (lows[t] < ll) ll = lows[t]
            }
            val rsv = if (hh == ll) 50.0 else (closes[i] - ll) / (hh - ll) * 100.0
            if (i == 0) {
                k[i] = rsv
                d[i] = rsv
            } else {
                k[i] = k[i - 1] * 2.0 / 3.0 + rsv / 3.0
                d[i] = d[i - 1] * 2.0 / 3.0 + k[i] / 3.0
            }
            j[i] = 3 * k[i] - 2 * d[i]
        }
        return Triple(k, d, j)
    }

    /** RSI：SMA 平滑法（Wilder 风格近似） */
    fun rsi(closes: DoubleArray, period: Int): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= period) return out
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) avgGain += diff else avgLoss -= diff
        }
        avgGain /= period
        avgLoss /= period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        for (i in period + 1 until size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        return out
    }

    /** BOLL(20,2) */
    fun boll(closes: DoubleArray, period: Int = 20, k: Double = 2.0): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val mid = ma(closes, period)
        val size = closes.size
        val upper = DoubleArray(size) { Double.NaN }
        val lower = DoubleArray(size) { Double.NaN }
        for (i in period - 1 until size) {
            var variance = 0.0
            for (t in (i - period + 1)..i) {
                val diff = closes[t] - mid[i]
                variance += diff * diff
            }
            val std = Math.sqrt(variance / period)
            upper[i] = mid[i] + k * std
            lower[i] = mid[i] - k * std
        }
        return Triple(mid, upper, lower)
    }

    /** 威廉指标 WR(N)：(HHV(HIGH,N)-CLOSE)/(HHV(HIGH,N)-LLV(LOW,N))*100 */
    fun wr(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int = 14): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        for (i in period - 1 until size) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (t in (i - period + 1)..i) {
                if (highs[t] > hh) hh = highs[t]
                if (lows[t] < ll) ll = lows[t]
            }
            out[i] = if (hh != ll) (hh - closes[i]) / (hh - ll) * 100 else 50.0
        }
        return out
    }

    /** 顺势指标 CCI(N)：(TP - MA(TP,N)) / (0.015 * MD)，TP=(HIGH+LOW+CLOSE)/3 */
    fun cci(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int = 14): DoubleArray {
        val size = closes.size
        val tp = DoubleArray(size) { (highs[it] + lows[it] + closes[it]) / 3.0 }
        val tpMa = ma(tp, period)
        val out = DoubleArray(size) { Double.NaN }
        for (i in period - 1 until size) {
            var md = 0.0
            for (t in (i - period + 1)..i) {
                md += abs(tp[t] - tpMa[i])
            }
            md /= period
            out[i] = if (md > 0) (tp[i] - tpMa[i]) / (0.015 * md) else 0.0
        }
        return out
    }

    /** 乖离率 BIAS(N)：(CLOSE-MA(CLOSE,N))/MA(CLOSE,N)*100 */
    fun bias(closes: DoubleArray, period: Int): DoubleArray {
        val maArr = ma(closes, period)
        return DoubleArray(closes.size) { i ->
            if (!maArr[i].isNaN() && maArr[i] != 0.0) (closes[i] - maArr[i]) / maArr[i] * 100 else Double.NaN
        }
    }

    /** 能量潮 OBV：上涨日加成交量，下跌日减成交量 */
    fun obv(closes: DoubleArray, volumes: DoubleArray): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { 0.0 }
        for (i in 1 until size) {
            when {
                closes[i] > closes[i - 1] -> out[i] = out[i - 1] + volumes[i]
                closes[i] < closes[i - 1] -> out[i] = out[i - 1] - volumes[i]
                else -> out[i] = out[i - 1]
            }
        }
        return out
    }

    /** 变动率 ROC(N)：(CLOSE-REF(CLOSE,N))/REF(CLOSE,N)*100 */
    fun roc(closes: DoubleArray, period: Int = 12): DoubleArray {
        return DoubleArray(closes.size) { i ->
            if (i >= period && closes[i - period] != 0.0)
                (closes[i] - closes[i - period]) / closes[i - period] * 100
            else Double.NaN
        }
    }

    /** DMI趋向指标(14)：返回 Triple(ADX, +DI, -DI) */
    fun dmi(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int = 14): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val size = closes.size
        val pdi = DoubleArray(size) { Double.NaN }
        val mdi = DoubleArray(size) { Double.NaN }
        val adx = DoubleArray(size) { Double.NaN }
        if (size < 2) return Triple(adx, pdi, mdi)

        val tr = DoubleArray(size)
        val plusDM = DoubleArray(size)
        val minusDM = DoubleArray(size)
        for (i in 1 until size) {
            val upMove = highs[i] - highs[i - 1]
            val downMove = lows[i - 1] - lows[i]
            plusDM[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDM[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
            tr[i] = maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
        }
        // 平滑
        var trSum = tr.take(period).sum()
        var plusSum = plusDM.take(period).sum()
        var minusSum = minusDM.take(period).sum()
        val dx = DoubleArray(size) { Double.NaN }
        for (i in period until size) {
            if (i > period) {
                trSum = trSum - trSum / period + tr[i]
                plusSum = plusSum - plusSum / period + plusDM[i]
                minusSum = minusSum - minusSum / period + minusDM[i]
            }
            val pdiVal = if (trSum > 0) plusSum / trSum * 100 else 0.0
            val mdiVal = if (trSum > 0) minusSum / trSum * 100 else 0.0
            pdi[i] = pdiVal
            mdi[i] = mdiVal
            dx[i] = if (pdiVal + mdiVal > 0) abs(pdiVal - mdiVal) / (pdiVal + mdiVal) * 100 else 0.0
        }
        // ADX = DX的period平均
        var dxSum = 0.0
        for (i in period until (period * 2).coerceAtMost(size)) {
            if (!dx[i].isNaN()) dxSum += dx[i]
        }
        if (size > period * 2) adx[period * 2 - 1] = dxSum / period
        for (i in period * 2 until size) {
            if (!adx[i - 1].isNaN() && !dx[i].isNaN()) {
                adx[i] = (adx[i - 1] * (period - 1) + dx[i]) / period
            }
        }
        return Triple(adx, pdi, mdi)
    }

    /** 抛物线 SAR(0.02, 0.2) */
    fun sar(highs: DoubleArray, lows: DoubleArray, afStart: Double = 0.02, afMax: Double = 0.2): DoubleArray {
        val size = highs.size
        val out = DoubleArray(size) { Double.NaN }
        if (size < 2) return out
        var long = true  // 多头
        var af = afStart
        var ep = highs[0]  // 极值
        var sarVal = lows[0]
        out[0] = sarVal
        for (i in 1 until size) {
            val prevSar = sarVal
            if (long) {
                sarVal = prevSar + af * (ep - prevSar)
                // 不能高于前两天最低价
                if (i >= 2) sarVal = minOf(sarVal, lows[i - 1], lows[i - 2])
                if (lows[i] < sarVal) {  // 转向
                    long = false
                    sarVal = ep
                    ep = lows[i]
                    af = afStart
                } else {
                    if (highs[i] > ep) { ep = highs[i]; af = minOf(af + afStart, afMax) }
                }
            } else {
                sarVal = prevSar + af * (ep - prevSar)
                if (i >= 2) sarVal = maxOf(sarVal, highs[i - 1], highs[i - 2])
                if (highs[i] > sarVal) {  // 转向
                    long = true
                    sarVal = ep
                    ep = highs[i]
                    af = afStart
                } else {
                    if (lows[i] < ep) { ep = lows[i]; af = minOf(af + afStart, afMax) }
                }
            }
            out[i] = sarVal
        }
        return out
    }

    /** 计算全部指标 */
    fun computeAll(kline: List<KlinePoint>): IndicatorResult {
        if (kline.isEmpty()) return IndicatorResult()
        val closes = kline.map { it.close }.toDoubleArray()
        val highs = kline.map { it.high }.toDoubleArray()
        val lows = kline.map { it.low }.toDoubleArray()
        val volumes = kline.map { it.volume }.toDoubleArray()
        val (dif, dea, macdArr) = macd(closes)
        val (kk, dd, jj) = kdj(highs, lows, closes)
        val (mid, up, low) = boll(closes)
        val (adxArr, pdiArr, mdiArr) = dmi(highs, lows, closes)
        // 新增指标计算
        val trArr = tr(highs, lows, closes)
        val atrArr = atr(highs, lows, closes)
        val (stochK, stochD) = stochastic(highs, lows, closes)
        val mfiArr = mfi(highs, lows, closes, volumes)
        val vwapArr = vwap(highs, lows, closes, volumes)
        val tsiArr = tsi(closes)
        val trixArr = trix(closes)
        val ultimateArr = ultimateOscillator(highs, lows, closes)
        val aoArr = awesomeOscillator(highs, lows)
        val cmfArr = chaikinMoneyFlow(highs, lows, closes, volumes)
        val (keltnerMid, keltnerUp, keltnerLow) = keltnerChannel(highs, lows, closes)
        val (donchianUp, donchianLow) = donchianChannel(highs, lows)
        val (superTrend, superTrendDir) = superTrend(highs, lows, closes)
        val (ichimokuTenkan, ichimokuKijun, ichimokuSenkouA) = ichimokuCloud(highs, lows)
        val rviArr = rvi(highs, lows, closes)
        val coppockArr = coppockCurve(closes)
        val (ppoLine, ppoSignal, ppoHist) = ppo(closes)
        val fisherArr = fisherTransform(highs, lows)
        return IndicatorResult(
            ma5 = ma(closes, 5),
            ma10 = ma(closes, 10),
            ma20 = ma(closes, 20),
            ma60 = ma(closes, 60),
            dif = dif, dea = dea, macd = macdArr,
            k = kk, d = dd, j = jj,
            rsi6 = rsi(closes, 6),
            rsi12 = rsi(closes, 12),
            rsi24 = rsi(closes, 24),
            bollMid = mid, bollUp = up, bollLow = low,
            ema12 = ema(closes, 12),
            ema26 = ema(closes, 26),
            wr = wr(highs, lows, closes),
            cci = cci(highs, lows, closes),
            adx = adxArr, pdi = pdiArr, mdi = mdiArr,
            bias6 = bias(closes, 6),
            bias12 = bias(closes, 12),
            sar = sar(highs, lows),
            obv = obv(closes, volumes),
            roc = roc(closes),
            // 新增指标
            atr = atrArr,
            tr = trArr,
            stochK = stochK,
            stochD = stochD,
            mfi = mfiArr,
            vwap = vwapArr,
            tsi = tsiArr,
            trix = trixArr,
            ultimate = ultimateArr,
            ao = aoArr,
            cmf = cmfArr,
            keltnerMid = keltnerMid,
            keltnerUp = keltnerUp,
            keltnerLow = keltnerLow,
            donchianUp = donchianUp,
            donchianLow = donchianLow,
            superTrend = superTrend,
            superTrendDir = superTrendDir,
            ichimokuTenkan = ichimokuTenkan,
            ichimokuKijun = ichimokuKijun,
            ichimokuSenkouA = ichimokuSenkouA,
            ichimokuSenkouB = ichimokuSenkouA, // 简化，实际需要单独计算
            rvi = rviArr,
            coppock = coppockArr,
            ema9 = ema(closes, 9),
            ema21 = ema(closes, 21),
            ema50 = ema(closes, 50),
            wma20 = wma(closes, 20),
            hma20 = hma(closes, 20),
            kama20 = kama(closes, 20),
            tema20 = tema(closes, 20),
            williamsR = wr(highs, lows, closes),
            ppo = ppoLine,
            qstick = qstick(closes, closes), // 简化，实际需要开盘价
            fisher = fisherArr,
        )
    }

    /** 最近的趋势判断摘要（供 AI prompt 使用） */
    fun summarize(kline: List<KlinePoint>, ind: IndicatorResult): String {
        if (kline.isEmpty()) return "无数据"
        val last = kline.last()
        val prev = if (kline.size >= 2) kline[kline.size - 2] else null
        val sb = StringBuilder()
        sb.append("最新收盘: %.2f".format(last.close))
        if (prev != null) {
            sb.append("（前日 %.2f，%+.2f%%）".format(prev.close, (last.close - prev.close) / prev.close * 100))
        }
        sb.append("\n")
        fun fmt(v: Double?) = if (v == null || v.isNaN()) "—" else "%.2f".format(v)
        fun fmtInt(v: Double?) = if (v == null || v.isNaN()) "—" else "%.1f".format(v)
        val n = kline.size - 1
        val ma5 = ind.ma5?.get(n) ?: Double.NaN
        val ma10 = ind.ma10?.get(n) ?: Double.NaN
        val ma20 = ind.ma20?.get(n) ?: Double.NaN
        sb.append("MA5=%s MA10=%s MA20=%s".format(fmt(ma5), fmt(ma10), fmt(ma20)))
        if (!ma5.isNaN() && !ma10.isNaN() && !ma20.isNaN()) {
            if (ma5 > ma10 && ma10 > ma20) sb.append("（多头排列）")
            else if (ma5 < ma10 && ma10 < ma20) sb.append("（空头排列）")
            else sb.append("（均线纠缠）")
        }
        sb.append("\n")
        val dif = ind.dif?.get(n) ?: Double.NaN
        val dea = ind.dea?.get(n) ?: Double.NaN
        val macd = ind.macd?.get(n) ?: Double.NaN
        sb.append("MACD: DIF=%s DEA=%s 柱=%s".format(fmt(dif), fmt(dea), fmt(macd)))
        if (!dif.isNaN() && !dea.isNaN()) {
            if (dif > dea && dif > 0) sb.append("（零上金叉，多头）")
            else if (dif > dea && dif < 0) sb.append("（零下金叉，反弹）")
            else if (dif < dea && dif > 0) sb.append("（零上死叉，回调）")
            else if (dif < dea && dif < 0) sb.append("（零下死叉，空头）")
        }
        sb.append("\n")
        val k = ind.k?.get(n) ?: Double.NaN
        val d = ind.d?.get(n) ?: Double.NaN
        val j = ind.j?.get(n) ?: Double.NaN
        sb.append("KDJ: K=%s D=%s J=%s".format(fmtInt(k), fmtInt(d), fmtInt(j)))
        if (!k.isNaN() && !d.isNaN()) {
            if (k > 80 && d > 80) sb.append("（超买区）")
            else if (k < 20 && d < 20) sb.append("（超卖区）")
            else if (k > d) sb.append("（金叉向上）")
            else sb.append("（死叉向下）")
        }
        sb.append("\n")
        val rsi6 = ind.rsi6?.get(n) ?: Double.NaN
        sb.append("RSI: RSI6=%s RSI12=%s RSI24=%s".format(fmtInt(ind.rsi6?.get(n)), fmtInt(ind.rsi12?.get(n)), fmtInt(ind.rsi24?.get(n))))
        if (!rsi6.isNaN()) {
            if (rsi6 > 70) sb.append("（超买）")
            else if (rsi6 < 30) sb.append("（超卖）")
        }
        sb.append("\n")
        val bollUp = ind.bollUp?.get(n) ?: Double.NaN
        val bollMid = ind.bollMid?.get(n) ?: Double.NaN
        val bollLow = ind.bollLow?.get(n) ?: Double.NaN
        sb.append("BOLL: 上轨=%s 中轨=%s 下轨=%s".format(fmt(bollUp), fmt(bollMid), fmt(bollLow)))
        if (!bollUp.isNaN() && !bollLow.isNaN() && last.close > 0) {
            if (last.close >= bollUp) sb.append("（触及上轨，强压力）")
            else if (last.close <= bollLow) sb.append("（触及下轨，强支撑）")
            else if (last.close > bollMid) sb.append("（中轨上方，偏强）")
            else sb.append("（中轨下方，偏弱）")
        }
        sb.append("\n")
        if (kline.size >= 20) {
            val vol5 = kline.takeLast(5).map { it.volume }.average()
            val vol20 = kline.takeLast(20).map { it.volume }.average()
            val volRatio = if (vol20 > 0) vol5 / vol20 else 1.0
            sb.append("量能: 5日均量%.0f手，20日均量%.0f手，量比%.2f".format(vol5, vol20, volRatio))
            if (volRatio > 1.5) sb.append("（明显放量）")
            else if (volRatio < 0.7) sb.append("（明显缩量）")
            else sb.append("（量能平稳）")
            sb.append("\n")
        }
        val recent = kline.takeLast(10)
        val startPrice = recent.first().close
        val periodPct = (last.close - startPrice) / startPrice * 100
        sb.append("近%d日涨跌幅: %+.2f%%\n".format(recent.size, periodPct))
        val hi = recent.maxOf { it.high }
        val lo = recent.minOf { it.low }
        sb.append("近%d日最高 %.2f，最低 %.2f\n".format(recent.size, hi, lo))
        return sb.toString()
    }

    /** 判断均线多头/空头排列 */
    fun maTrend(ind: IndicatorResult, n: Int): String {
        val m5 = ind.ma5?.getOrNull(n)
        val m10 = ind.ma10?.getOrNull(n)
        val m20 = ind.ma20?.getOrNull(n)
        if (m5 == null || m10 == null || m20 == null) return "数据不足"
        if (m5.isNaN() || m10.isNaN() || m20.isNaN()) return "数据不足"
        return when {
            m5 > m10 && m10 > m20 -> "多头排列"
            m5 < m10 && m10 < m20 -> "空头排列"
            else -> "均线纠缠"
        }
    }

    fun fmtAbs(v: Double): String = "%.2f".format(abs(v))

    // ========== 新增特色指标 ==========

    /** 真实波幅 TR */
    fun tr(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray): DoubleArray {
        val out = DoubleArray(highs.size) { Double.NaN }
        if (highs.isEmpty()) return out
        out[0] = highs[0] - lows[0]
        for (i in 1 until highs.size) {
            val tr = maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
            out[i] = tr
        }
        return out
    }

    /** 平均真实波幅 ATR(14) */
    fun atr(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int = 14): DoubleArray {
        val trArr = tr(highs, lows, closes)
        val out = DoubleArray(highs.size) { Double.NaN }
        if (highs.size <= period) return out
        // Wilder 平滑
        var sum = 0.0
        for (i in 0 until period) sum += trArr[i]
        out[period - 1] = sum / period
        for (i in period until highs.size) {
            out[i] = (out[i - 1] * (period - 1) + trArr[i]) / period
        }
        return out
    }

    /** 随机指标 Stochastic(14,3) */
    fun stochastic(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, kPeriod: Int = 14, dPeriod: Int = 3): Pair<DoubleArray, DoubleArray> {
        val size = closes.size
        val k = DoubleArray(size) { Double.NaN }
        for (i in kPeriod - 1 until size) {
            val start = i - kPeriod + 1
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (t in start..i) {
                if (highs[t] > hh) hh = highs[t]
                if (lows[t] < ll) ll = lows[t]
            }
            k[i] = if (hh == ll) 50.0 else (closes[i] - ll) / (hh - ll) * 100.0
        }
        val d = ma(k, dPeriod)
        return Pair(k, d)
    }

    /** 资金流量指数 MFI(14) */
    fun mfi(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, volumes: DoubleArray, period: Int = 14): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= period) return out
        val typicalPrice = DoubleArray(size) { (highs[it] + lows[it] + closes[it]) / 3.0 }
        val moneyFlow = DoubleArray(size) { typicalPrice[it] * volumes[it] }
        for (i in period until size) {
            var positiveFlow = 0.0
            var negativeFlow = 0.0
            for (t in i - period + 1..i) {
                if (typicalPrice[t] > typicalPrice[t - 1]) positiveFlow += moneyFlow[t]
                else if (typicalPrice[t] < typicalPrice[t - 1]) negativeFlow += moneyFlow[t]
            }
            out[i] = if (negativeFlow == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + positiveFlow / negativeFlow)
        }
        return out
    }

    /** 成交量加权平均价 VWAP */
    fun vwap(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, volumes: DoubleArray): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        var cumulativePV = 0.0
        var cumulativeV = 0.0
        for (i in 0 until size) {
            val typicalPrice = (highs[i] + lows[i] + closes[i]) / 3.0
            cumulativePV += typicalPrice * volumes[i]
            cumulativeV += volumes[i]
            out[i] = if (cumulativeV > 0) cumulativePV / cumulativeV else Double.NaN
        }
        return out
    }

    /** 真实强度指数 TSI */
    fun tsi(closes: DoubleArray, fast: Int = 25, slow: Int = 13): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= fast + slow) return out
        val momentum = DoubleArray(size) { if (it == 0) 0.0 else closes[it] - closes[it - 1] }
        val absMomentum = DoubleArray(size) { abs(momentum[it]) }
        val emaFast1 = ema(momentum, fast)
        val emaSlow1 = ema(emaFast1, slow)
        val emaFast2 = ema(absMomentum, fast)
        val emaSlow2 = ema(emaFast2, slow)
        for (i in 0 until size) {
            out[i] = if (emaSlow2[i] != 0.0 && !emaSlow2[i].isNaN()) emaSlow1[i] / emaSlow2[i] * 100.0 else Double.NaN
        }
        return out
    }

    /** 三重指数平均 TRIX(15) */
    fun trix(closes: DoubleArray, period: Int = 15): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= period * 3) return out
        val ema1 = ema(closes, period)
        val ema2 = ema(ema1, period)
        val ema3 = ema(ema2, period)
        for (i in 1 until size) {
            if (!ema3[i].isNaN() && !ema3[i - 1].isNaN() && ema3[i - 1] != 0.0) {
                out[i] = (ema3[i] - ema3[i - 1]) / ema3[i - 1] * 100.0
            }
        }
        return out
    }

    /** 终极振荡器 Ultimate Oscillator(7,14,28) */
    fun ultimateOscillator(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, p1: Int = 7, p2: Int = 14, p3: Int = 28): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= p3) return out
        val buyingPressure = DoubleArray(size) { Double.NaN }
        val trueRange = DoubleArray(size) { Double.NaN }
        for (i in 1 until size) {
            buyingPressure[i] = closes[i] - minOf(lows[i], closes[i - 1])
            trueRange[i] = maxOf(highs[i], closes[i - 1]) - minOf(lows[i], closes[i - 1])
        }
        fun avgBP(period: Int, idx: Int): Double {
            var sum = 0.0
            for (t in idx - period + 1..idx) sum += buyingPressure[t]
            return sum
        }
        fun avgTR(period: Int, idx: Int): Double {
            var sum = 0.0
            for (t in idx - period + 1..idx) sum += trueRange[t]
            return sum
        }
        for (i in p3 until size) {
            val avg1 = if (avgTR(p1, i) > 0) avgBP(p1, i) / avgTR(p1, i) else 0.0
            val avg2 = if (avgTR(p2, i) > 0) avgBP(p2, i) / avgTR(p2, i) else 0.0
            val avg3 = if (avgTR(p3, i) > 0) avgBP(p3, i) / avgTR(p3, i) else 0.0
            out[i] = 100.0 * (4.0 * avg1 + 2.0 * avg2 + avg3) / 7.0
        }
        return out
    }

    /** 动量震荡 Awesome Oscillator */
    fun awesomeOscillator(highs: DoubleArray, lows: DoubleArray): DoubleArray {
        val size = highs.size
        val out = DoubleArray(size) { Double.NaN }
        val median = DoubleArray(size) { (highs[it] + lows[it]) / 2.0 }
        val ma5 = ma(median, 5)
        val ma34 = ma(median, 34)
        for (i in 0 until size) {
            if (!ma5[i].isNaN() && !ma34[i].isNaN()) out[i] = ma5[i] - ma34[i]
        }
        return out
    }

    /** 佳庆资金流 CMF(20) */
    fun chaikinMoneyFlow(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, volumes: DoubleArray, period: Int = 20): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= period) return out
        val mfm = DoubleArray(size) {
            val range = highs[it] - lows[it]
            if (range == 0.0) 0.0 else ((closes[it] - lows[it]) - (highs[it] - closes[it])) / range
        }
        val mfv = DoubleArray(size) { mfm[it] * volumes[it] }
        for (i in period - 1 until size) {
            var sumMFV = 0.0
            var sumVol = 0.0
            for (t in i - period + 1..i) {
                sumMFV += mfv[t]
                sumVol += volumes[t]
            }
            out[i] = if (sumVol > 0) sumMFV / sumVol else 0.0
        }
        return out
    }

    /** 肯特纳通道 Keltner(20,2) */
    fun keltnerChannel(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int = 20, multiplier: Double = 2.0): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val mid = ema(closes, period)
        val atrArr = atr(highs, lows, closes, period)
        val up = DoubleArray(closes.size) { if (!mid[it].isNaN() && !atrArr[it].isNaN()) mid[it] + multiplier * atrArr[it] else Double.NaN }
        val low = DoubleArray(closes.size) { if (!mid[it].isNaN() && !atrArr[it].isNaN()) mid[it] - multiplier * atrArr[it] else Double.NaN }
        return Triple(mid, up, low)
    }

    /** 唐奇安通道 Donchian(20) */
    fun donchianChannel(highs: DoubleArray, lows: DoubleArray, period: Int = 20): Pair<DoubleArray, DoubleArray> {
        val size = highs.size
        val up = DoubleArray(size) { Double.NaN }
        val low = DoubleArray(size) { Double.NaN }
        for (i in period - 1 until size) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (t in i - period + 1..i) {
                if (highs[t] > hh) hh = highs[t]
                if (lows[t] < ll) ll = lows[t]
            }
            up[i] = hh
            low[i] = ll
        }
        return Pair(up, low)
    }

    /** 超级趋势 SuperTrend(10,3) */
    fun superTrend(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int = 10, multiplier: Double = 3.0): Pair<DoubleArray, DoubleArray> {
        val size = closes.size
        val st = DoubleArray(size) { Double.NaN }
        val dir = DoubleArray(size) { 1.0 }
        if (size <= period) return Pair(st, dir)
        val atrArr = atr(highs, lows, closes, period)
        val hl2 = DoubleArray(size) { (highs[it] + lows[it]) / 2.0 }
        var finalUpper = hl2[period - 1] + multiplier * atrArr[period - 1]
        var finalLower = hl2[period - 1] - multiplier * atrArr[period - 1]
        st[period - 1] = finalUpper
        dir[period - 1] = -1.0
        for (i in period until size) {
            val upper = hl2[i] + multiplier * atrArr[i]
            val lower = hl2[i] - multiplier * atrArr[i]
            finalUpper = if (upper < finalUpper || closes[i - 1] > finalUpper) upper else finalUpper
            finalLower = if (lower > finalLower || closes[i - 1] < finalLower) lower else finalLower
            if (dir[i - 1] == -1.0 && closes[i] > finalUpper) {
                dir[i] = 1.0
                st[i] = finalLower
            } else if (dir[i - 1] == 1.0 && closes[i] < finalLower) {
                dir[i] = -1.0
                st[i] = finalUpper
            } else {
                dir[i] = dir[i - 1]
                st[i] = if (dir[i] == 1.0) finalLower else finalUpper
            }
        }
        return Pair(st, dir)
    }

    /** 一目均衡表 Ichimoku Cloud(9,26,52) */
    fun ichimokuCloud(highs: DoubleArray, lows: DoubleArray, tenkanPeriod: Int = 9, kijunPeriod: Int = 26, senkouPeriod: Int = 52): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val size = highs.size
        val tenkan = DoubleArray(size) { Double.NaN }
        val kijun = DoubleArray(size) { Double.NaN }
        val senkouA = DoubleArray(size) { Double.NaN }
        val senkouB = DoubleArray(size) { Double.NaN }
        fun donchianMid(period: Int, idx: Int): Double {
            if (idx < period - 1) return Double.NaN
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (t in idx - period + 1..idx) {
                if (highs[t] > hh) hh = highs[t]
                if (lows[t] < ll) ll = lows[t]
            }
            return (hh + ll) / 2.0
        }
        for (i in 0 until size) {
            tenkan[i] = donchianMid(tenkanPeriod, i)
            kijun[i] = donchianMid(kijunPeriod, i)
            if (!tenkan[i].isNaN() && !kijun[i].isNaN()) senkouA[i] = (tenkan[i] + kijun[i]) / 2.0
            senkouB[i] = donchianMid(senkouPeriod, i)
        }
        return Triple(tenkan, kijun, senkouA)
    }

    /** 相对活力指数 RVI(10) */
    fun rvi(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, opens: DoubleArray? = null, period: Int = 10): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= period) return out
        val change = DoubleArray(size) { if (it == 0) 0.0 else closes[it] - closes[it - 1] }
        val range = DoubleArray(size) { highs[it] - lows[it] }
        for (i in period - 1 until size) {
            var sumChange = 0.0
            var sumRange = 0.0
            for (t in i - period + 1..i) {
                sumChange += change[t]
                sumRange += range[t]
            }
            out[i] = if (sumRange > 0) sumChange / sumRange * 100.0 else 0.0
        }
        return out
    }

    /** 科普曲线 Coppock(14,11,10) */
    fun coppockCurve(closes: DoubleArray, roc1: Int = 14, roc2: Int = 11, wmaPeriod: Int = 10): DoubleArray {
        val size = closes.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= roc1 + wmaPeriod) return out
        val rocArr1 = roc(closes, roc1)
        val rocArr2 = roc(closes, roc2)
        val sum = DoubleArray(size) { if (!rocArr1[it].isNaN() && !rocArr2[it].isNaN()) rocArr1[it] + rocArr2[it] else Double.NaN }
        val wma = wma(sum, wmaPeriod)
        for (i in 0 until size) out[i] = wma[i]
        return out
    }

    /** 加权移动平均 WMA */
    fun wma(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.size < period) return out
        val denom = period * (period + 1) / 2.0
        for (i in period - 1 until values.size) {
            var sum = 0.0
            for (t in 0 until period) {
                sum += values[i - period + 1 + t] * (t + 1)
            }
            out[i] = sum / denom
        }
        return out
    }

    /** 赫尔移动平均 HMA */
    fun hma(values: DoubleArray, period: Int): DoubleArray {
        val wma1 = wma(values, period / 2)
        val wma2 = wma(values, period)
        val diff = DoubleArray(values.size) { if (!wma1[it].isNaN() && !wma2[it].isNaN()) 2 * wma1[it] - wma2[it] else Double.NaN }
        return wma(diff, kotlin.math.sqrt(period.toDouble()).toInt())
    }

    /** 考夫曼自适应移动平均 KAMA */
    fun kama(values: DoubleArray, period: Int = 10, fast: Int = 2, slow: Int = 30): DoubleArray {
        val size = values.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= period) return out
        val fastSC = 2.0 / (fast + 1)
        val slowSC = 2.0 / (slow + 1)
        out[period] = values[period]
        for (i in period + 1 until size) {
            var change = abs(values[i] - values[i - period])
            var volatility = 0.0
            for (t in i - period + 1..i) volatility += abs(values[t] - values[t - 1])
            val er = if (volatility > 0) change / volatility else 0.0
            val sc = (er * (fastSC - slowSC) + slowSC) * (er * (fastSC - slowSC) + slowSC)
            out[i] = out[i - 1] + sc * (values[i] - out[i - 1])
        }
        return out
    }

    /** 三重指数移动平均 TEMA */
    fun tema(values: DoubleArray, period: Int): DoubleArray {
        val ema1 = ema(values, period)
        val ema2 = ema(ema1, period)
        val ema3 = ema(ema2, period)
        val out = DoubleArray(values.size) { if (!ema1[it].isNaN() && !ema2[it].isNaN() && !ema3[it].isNaN()) 3 * ema1[it] - 3 * ema2[it] + ema3[it] else Double.NaN }
        return out
    }

    /** 百分比价格振荡器 PPO */
    fun ppo(closes: DoubleArray, fast: Int = 12, slow: Int = 26, signal: Int = 9): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val ppoLine = DoubleArray(closes.size) { if (!emaSlow[it].isNaN() && emaSlow[it] != 0.0) (emaFast[it] - emaSlow[it]) / emaSlow[it] * 100.0 else Double.NaN }
        val signalLine = ema(ppoLine, signal)
        val histogram = DoubleArray(closes.size) { if (!ppoLine[it].isNaN() && !signalLine[it].isNaN()) ppoLine[it] - signalLine[it] else Double.NaN }
        return Triple(ppoLine, signalLine, histogram)
    }

    /** Qstick */
    fun qstick(opens: DoubleArray, closes: DoubleArray, period: Int = 10): DoubleArray {
        val diff = DoubleArray(opens.size) { closes[it] - opens[it] }
        return ma(diff, period)
    }

    /** Fisher变换 */
    fun fisherTransform(highs: DoubleArray, lows: DoubleArray, period: Int = 10): DoubleArray {
        val size = highs.size
        val out = DoubleArray(size) { Double.NaN }
        if (size <= period) return out
        val median = DoubleArray(size) { (highs[it] + lows[it]) / 2.0 }
        var fisher = 0.0
        var value = 0.0
        for (i in period - 1 until size) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (t in i - period + 1..i) {
                if (median[t] > hh) hh = median[t]
                if (median[t] < ll) ll = median[t]
            }
            val range = hh - ll
            value = if (range > 0) 0.66 * ((median[i] - ll) / range - 0.5) + 0.67 * value else value
            value = value.coerceIn(-0.999, 0.999)
            fisher = 0.5 * kotlin.math.ln((1 + value) / (1 - value)) + 0.5 * fisher
            out[i] = fisher
        }
        return out
    }
}
