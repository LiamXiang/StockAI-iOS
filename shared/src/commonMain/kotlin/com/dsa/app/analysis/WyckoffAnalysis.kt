package com.dsa.app.analysis

import com.dsa.app.util.Fmt

import com.dsa.app.data.KlinePoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 威科夫量价分析
 * 实现核心威科夫理论：交易区间识别、阶段判断、量价分析、信号识别
 */
object WyckoffAnalysis {

    /** 威科夫阶段枚举 */
    enum class Stage {
        ACCUMULATION,    // 吸筹
        MARKUP,          // 上涨
        DISTRIBUTION,    // 派发
        MARKDOWN,        // 下跌
        UNKNOWN          // 未知
    }

    /** 威科夫信号 */
    data class WyckoffSignal(
        val type: String,           // 信号类型
        val description: String,    // 信号描述
        val confidence: Double,     // 置信度 0-1
        val index: Int              // 出现位置
    )

    /** 交易区间 */
    data class TradingRange(
        val support: Double,        // 支撑位
        val resistance: Double,     // 阻力位
        val mid: Double,            // 中枢
        val widthPct: Double,       // 区间宽度百分比
        val supportTests: Int,      // 支撑测试次数
        val resistanceTests: Int,   // 阻力测试次数
        val qualityScore: Double    // 质量评分 0-1
    )

    /** 威科夫分析结果 */
    data class WyckoffResult(
        val stage: Stage,                    // 当前阶段
        val stageDescription: String,       // 阶段描述
        val tradingRange: TradingRange?,    // 交易区间
        val signals: List<WyckoffSignal>,   // 近期信号
        val volumeAnalysis: String,         // 成交量分析
        val trendAnalysis: String,          // 趋势分析
        val suggestion: String              // 操作建议
    )

    /**
     * 执行威科夫分析
     */
    fun analyze(klines: List<KlinePoint>): WyckoffResult {
        if (klines.size < 60) {
            return WyckoffResult(
                stage = Stage.UNKNOWN,
                stageDescription = "数据不足，无法进行威科夫分析",
                tradingRange = null,
                signals = emptyList(),
                volumeAnalysis = "数据不足",
                trendAnalysis = "数据不足",
                suggestion = "建议至少使用60根日K线进行分析"
            )
        }

        val closes = klines.map { it.close }.toDoubleArray()
        val highs = klines.map { it.high }.toDoubleArray()
        val lows = klines.map { it.low }.toDoubleArray()
        val volumes = klines.map { it.volume }.toDoubleArray()
        val n = klines.size

        // 1. 识别交易区间
        val tradingRange = detectTradingRange(highs, lows, closes, volumes)

        // 2. 判断当前阶段
        val stage = detectStage(closes, highs, lows, volumes, tradingRange)

        // 3. 识别信号
        val signals = detectSignals(highs, lows, closes, volumes, tradingRange)

        // 4. 成交量分析
        val volumeAnalysis = analyzeVolume(closes, volumes, tradingRange)

        // 5. 趋势分析
        val trendAnalysis = analyzeTrend(closes, highs, lows)

        // 6. 操作建议
        val suggestion = generateSuggestion(stage, signals, tradingRange, closes.last())

        return WyckoffResult(
            stage = stage,
            stageDescription = getStageDescription(stage),
            tradingRange = tradingRange,
            signals = signals,
            volumeAnalysis = volumeAnalysis,
            trendAnalysis = trendAnalysis,
            suggestion = suggestion
        )
    }

    /**
     * 识别交易区间
     */
    private fun detectTradingRange(
        highs: DoubleArray,
        lows: DoubleArray,
        closes: DoubleArray,
        volumes: DoubleArray
    ): TradingRange? {
        val n = closes.size
        val lookback = min(60, n)
        val start = n - lookback

        val recentHighs = highs.copyOfRange(start, n)
        val recentLows = lows.copyOfRange(start, n)
        val recentCloses = closes.copyOfRange(start, n)
        val recentVolumes = volumes.copyOfRange(start, n)

        // 计算摆动高低点
        val swingHighs = mutableListOf<Double>()
        val swingLows = mutableListOf<Double>()

        for (i in 2 until lookback - 2) {
            // 局部高点
            if (recentHighs[i] > recentHighs[i-1] && recentHighs[i] > recentHighs[i-2] &&
                recentHighs[i] > recentHighs[i+1] && recentHighs[i] > recentHighs[i+2]) {
                swingHighs.add(recentHighs[i])
            }
            // 局部低点
            if (recentLows[i] < recentLows[i-1] && recentLows[i] < recentLows[i-2] &&
                recentLows[i] < recentLows[i+1] && recentLows[i] < recentLows[i+2]) {
                swingLows.add(recentLows[i])
            }
        }

        if (swingHighs.size < 2 || swingLows.size < 2) return null

        // 计算支撑阻力
        val resistance = swingHighs.average()
        val support = swingLows.average()
        val mid = (support + resistance) / 2
        val widthPct = (resistance - support) / mid * 100

        // 计算测试次数
        val resistanceTests = swingHighs.size
        val supportTests = swingLows.size

        // 计算质量评分
        val qualityScore = calculateRangeQuality(
            recentHighs, recentLows, recentCloses, recentVolumes,
            support, resistance
        )

        return TradingRange(
            support = support,
            resistance = resistance,
            mid = mid,
            widthPct = widthPct,
            supportTests = supportTests,
            resistanceTests = resistanceTests,
            qualityScore = qualityScore
        )
    }

    /**
     * 计算区间质量评分
     */
    private fun calculateRangeQuality(
        highs: DoubleArray,
        lows: DoubleArray,
        closes: DoubleArray,
        volumes: DoubleArray,
        support: Double,
        resistance: Double
    ): Double {
        var score = 0.0

        // 1. 价格在区间内的时间占比 (40%)
        var inRangeCount = 0
        for (c in closes) {
            if (c in support * 0.98..resistance * 1.02) {
                inRangeCount++
            }
        }
        score += (inRangeCount.toDouble() / closes.size) * 0.4

        // 2. 支撑阻力测试次数 (30%)
        val testScore = min(1.0, (min(highs.size, lows.size).toDouble() / 8))
        score += testScore * 0.3

        // 3. 成交量稳定性 (30%)
        val volMean = volumes.average()
        val volStd = kotlin.math.sqrt(volumes.map { (it - volMean) * (it - volMean) }.average())
        val volCV = volStd / volMean // 变异系数
        val volScore = max(0.0, 1.0 - volCV)
        score += volScore * 0.3

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * 判断当前阶段
     */
    private fun detectStage(
        closes: DoubleArray,
        highs: DoubleArray,
        lows: DoubleArray,
        volumes: DoubleArray,
        tradingRange: TradingRange?
    ): Stage {
        val n = closes.size
        val lastClose = closes.last()

        // 计算趋势指标
        val ma20 = calculateMA(closes, 20)
        val ma50 = calculateMA(closes, 50)

        // 计算近期涨跌幅
        val pct20 = (closes.last() - closes[max(0, n-20)]) / closes[max(0, n-20)] * 100
        val pct50 = (closes.last() - closes[max(0, n-50)]) / closes[max(0, n-50)] * 100

        // 计算成交量变化
        val volRecent = volumes.takeLast(10).average()
        val volPrev = volumes.takeLast(30).take(20).average()
        val volRatio = volRecent / volPrev

        return when {
            // 有明确交易区间
            tradingRange != null && tradingRange.qualityScore > 0.5 -> {
                when {
                    // 价格在区间下半部，成交量萎缩 -> 吸筹
                    lastClose < tradingRange.mid && volRatio < 0.9 -> Stage.ACCUMULATION
                    // 价格在区间上半部，成交量放大 -> 派发
                    lastClose > tradingRange.mid && volRatio > 1.1 -> Stage.DISTRIBUTION
                    // 价格突破区间上沿 -> 上涨
                    lastClose > tradingRange.resistance * 1.02 -> Stage.MARKUP
                    // 价格跌破区间下沿 -> 下跌
                    lastClose < tradingRange.support * 0.98 -> Stage.MARKDOWN
                    // 其他情况根据位置判断
                    lastClose < tradingRange.mid -> Stage.ACCUMULATION
                    else -> Stage.DISTRIBUTION
                }
            }
            // 无明确区间，根据趋势判断
            else -> {
                when {
                    pct20 > 5 && pct50 > 10 && ma20.last() > ma50.last() -> Stage.MARKUP
                    pct20 < -5 && pct50 < -10 && ma20.last() < ma50.last() -> Stage.MARKDOWN
                    pct20 > 0 -> Stage.MARKUP
                    else -> Stage.MARKDOWN
                }
            }
        }
    }

    /**
     * 识别威科夫信号
     */
    private fun detectSignals(
        highs: DoubleArray,
        lows: DoubleArray,
        closes: DoubleArray,
        volumes: DoubleArray,
        tradingRange: TradingRange?
    ): List<WyckoffSignal> {
        val signals = mutableListOf<WyckoffSignal>()
        val n = closes.size

        if (n < 30) return signals

        // 1. 识别 Spring（弹簧效应）- 跌破支撑后快速收回
        for (i in 20 until n) {
            if (tradingRange == null) continue
            // 跌破支撑
            if (lows[i] < tradingRange.support * 0.99) {
                // 后续2-3天收回
                val recover = closes.copyOfRange(i, min(n, i+3)).any { it > tradingRange.support }
                if (recover) {
                    // 成交量放大
                    val volRatio = volumes[i] / volumes.takeLast(20).average()
                    if (volRatio > 1.2) {
                        signals.add(WyckoffSignal(
                            type = "Spring",
                            description = "弹簧效应：跌破支撑后快速收回，可能是诱空",
                            confidence = min(1.0, volRatio / 2),
                            index = i
                        ))
                    }
                }
            }
        }

        // 2. 识别 Upthrust（上冲回落）- 突破阻力后快速回落
        for (i in 20 until n) {
            if (tradingRange == null) continue
            // 突破阻力
            if (highs[i] > tradingRange.resistance * 1.01) {
                // 后续2-3天回落
                val fail = closes.copyOfRange(i, min(n, i+3)).any { it < tradingRange.resistance }
                if (fail) {
                    // 成交量放大
                    val volRatio = volumes[i] / volumes.takeLast(20).average()
                    if (volRatio > 1.2) {
                        signals.add(WyckoffSignal(
                            type = "Upthrust",
                            description = "上冲回落：突破阻力后快速回落，可能是诱多",
                            confidence = min(1.0, volRatio / 2),
                            index = i
                        ))
                    }
                }
            }
        }

        // 3. 识别放量突破
        val ma20 = calculateMA(closes, 20)
        for (i in 20 until n) {
            val volRatio = volumes[i] / volumes.takeLast(20).average()
            // 放量上涨突破
            if (closes[i] > ma20[i] && volRatio > 1.5 && closes[i] > closes[i-1] * 1.02) {
                signals.add(WyckoffSignal(
                    type = "BreakoutUp",
                    description = "放量突破：成交量放大配合价格上涨突破",
                    confidence = min(1.0, volRatio / 3),
                    index = i
                ))
            }
            // 放量下跌突破
            if (closes[i] < ma20[i] && volRatio > 1.5 && closes[i] < closes[i-1] * 0.98) {
                signals.add(WyckoffSignal(
                    type = "BreakoutDown",
                    description = "放量下跌：成交量放大配合价格下跌",
                    confidence = min(1.0, volRatio / 3),
                    index = i
                ))
            }
        }

        // 4. 识别缩量回调
        for (i in 20 until n) {
            val volRatio = volumes[i] / volumes.takeLast(20).average()
            // 缩量下跌
            if (closes[i] < closes[i-1] && volRatio < 0.7) {
                signals.add(WyckoffSignal(
                    type = "ShrinkDown",
                    description = "缩量回调：下跌时成交量萎缩，抛压减轻",
                    confidence = (1.0 - volRatio).coerceIn(0.3, 0.8),
                    index = i
                ))
            }
        }

        // 只返回最近的信号
        return signals.sortedByDescending { it.index }.take(5)
    }

    /**
     * 成交量分析
     */
    private fun analyzeVolume(
        closes: DoubleArray,
        volumes: DoubleArray,
        tradingRange: TradingRange?
    ): String {
        val n = volumes.size
        val volRecent = volumes.takeLast(10).average()
        val volPrev = volumes.takeLast(30).take(20).average()
        val volRatio = volRecent / volPrev

        val priceTrend = if (closes.last() > closes[max(0, n-10)]) "上涨" else "下跌"

        return when {
            volRatio > 1.3 && priceTrend == "上涨" ->
                "成交量明显放大，配合价格$priceTrend，多头力量较强"
            volRatio > 1.3 && priceTrend == "下跌" ->
                "成交量明显放大，配合价格$priceTrend，空头力量较强"
            volRatio < 0.7 && priceTrend == "上涨" ->
                "成交量萎缩，价格$priceTrend，上涨动能不足"
            volRatio < 0.7 && priceTrend == "下跌" ->
                "成交量萎缩，价格$priceTrend，下跌动能减弱"
            else ->
                "成交量相对平稳，价格$priceTrend，市场处于均衡状态"
        }
    }

    /**
     * 趋势分析
     */
    private fun analyzeTrend(
        closes: DoubleArray,
        highs: DoubleArray,
        lows: DoubleArray
    ): String {
        val n = closes.size

        val ma5 = calculateMA(closes, 5)
        val ma20 = calculateMA(closes, 20)
        val ma60 = calculateMA(closes, 60)

        val trend = when {
            ma5.last() > ma20.last() && ma20.last() > ma60.last() -> "上升趋势"
            ma5.last() < ma20.last() && ma20.last() < ma60.last() -> "下降趋势"
            else -> "震荡趋势"
        }

        // 计算波动
        val recentHigh = highs.takeLast(20).max()
        val recentLow = lows.takeLast(20).min()
        val amplitude = (recentHigh - recentLow) / closes.last() * 100

        return "当前处于$trend，近20日振幅${Fmt.d(amplitude, 1)}%"
    }

    /**
     * 生成操作建议
     */
    private fun generateSuggestion(
        stage: Stage,
        signals: List<WyckoffSignal>,
        tradingRange: TradingRange?,
        currentPrice: Double
    ): String {
        val baseSuggestion = when (stage) {
            Stage.ACCUMULATION -> "处于吸筹阶段，可考虑逢低布局"
            Stage.MARKUP -> "处于上涨阶段，可持有或逢低加仓"
            Stage.DISTRIBUTION -> "处于派发阶段，注意风险，可考虑减仓"
            Stage.MARKDOWN -> "处于下跌阶段，建议观望或止损"
            Stage.UNKNOWN -> "趋势不明，建议观望"
        }

        // 根据信号调整建议
        val hasBuySignal = signals.any { it.type in listOf("Spring", "BreakoutUp", "ShrinkDown") }
        val hasSellSignal = signals.any { it.type in listOf("Upthrust", "BreakoutDown") }

        return when {
            hasBuySignal && stage == Stage.ACCUMULATION ->
                "$baseSuggestion，出现买入信号，可考虑建仓"
            hasBuySignal && stage == Stage.MARKUP ->
                "$baseSuggestion，出现加仓信号，可考虑加仓"
            hasSellSignal && stage == Stage.DISTRIBUTION ->
                "$baseSuggestion，出现卖出信号，可考虑减仓"
            hasSellSignal && stage == Stage.MARKDOWN ->
                "$baseSuggestion，继续回避"
            tradingRange != null ->
                "$baseSuggestion，区间支撑${Fmt.d(tradingRange.support)}，阻力${Fmt.d(tradingRange.resistance)}"
            else -> baseSuggestion
        }
    }

    /**
     * 获取阶段描述
     */
    private fun getStageDescription(stage: Stage): String {
        return when (stage) {
            Stage.ACCUMULATION -> "吸筹阶段：主力资金在低位收集筹码，股价波动较小，成交量逐步萎缩"
            Stage.MARKUP -> "上涨阶段：主力完成吸筹后开始拉升，股价持续上涨，成交量放大"
            Stage.DISTRIBUTION -> "派发阶段：主力在高位逐步出货，股价波动加大，成交量放大"
            Stage.MARKDOWN -> "下跌阶段：主力完成派发后股价持续下跌，成交量萎缩"
            Stage.UNKNOWN -> "数据不足，无法判断阶段"
        }
    }

    /**
     * 计算简单移动平均
     */
    private fun calculateMA(values: DoubleArray, period: Int): DoubleArray {
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
}
