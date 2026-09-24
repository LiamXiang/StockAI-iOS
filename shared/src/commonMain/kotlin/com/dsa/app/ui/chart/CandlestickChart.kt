package com.dsa.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.analysis.Indicators
import com.dsa.app.data.IndicatorResult
import com.dsa.app.data.KlinePoint
import com.dsa.app.ui.theme.DsaGreen
import com.dsa.app.ui.theme.DsaRed
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 副图指标类型 */
enum class IndicatorMode {
    MA, MACD, KDJ, RSI, BOLL, WR, CCI, DMI, BIAS, OBV, ROC,
    ATR, STOCH, MFI, VWAP, TSI, TRIX, ULTIMATE, AO, CMF,
    KELTNER, DONCHIAN, SUPERTREND, ICHIMOKU, RVI, COPPOCK, PPO, QSTICK, FISHER
}

private val Ma5Color = Color(0xFFF59E0B)
private val Ma10Color = Color(0xFF3B82F6)
private val Ma20Color = Color(0xFF8B5CF6)
private val BgGrid = Color(0x22000000)
private val LabelColor = Color(0xFF8A8F99)
private val CrosshairColor = Color(0xFF8A8F99)
private val TextStyleSmall = TextStyle(color = LabelColor, fontSize = 9.sp)
private val TextStyleValue = TextStyle(fontSize = 9.sp)

/**
 * K 线图：蜡烛 + MA/BOLL 叠加 + 成交量 + 副图指标
 * 支持水平拖动查看历史、双指缩放、点击十字光标查看当日详情。
 */
@Composable
fun CandlestickChart(
    kline: List<KlinePoint>,
    indicator: IndicatorResult,
    mode: IndicatorMode,
    modifier: Modifier = Modifier,
) {
    if (kline.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()

    val dataSize = kline.size
    var endIndex by remember(dataSize) { mutableIntStateOf(dataSize) }
    var visibleCount by remember(dataSize) { mutableFloatStateOf(minOf(80f, dataSize.toFloat())) }
    // 十字光标：选中的K线索引（相对于完整kline），null表示未选中
    var crosshairIdx by remember { mutableStateOf<Int?>(null) }
    var crosshairY by remember { mutableFloatStateOf(0f) }

    val vc = visibleCount.toInt().coerceAtMost(dataSize).coerceAtLeast(1)
    val start = max(0, min(endIndex - vc, dataSize - 1))
    val end = min(dataSize, max(start + 1, endIndex))
    val view = if (start < end) kline.subList(start, end) else emptyList()

    Canvas(
        modifier = modifier
            .pointerInput(dataSize) {
                detectTransformGestures { _, pan, zoom, _ ->
                    try {
                        visibleCount = (visibleCount / zoom).coerceIn(20f, minOf(150f, dataSize.toFloat()))
                        val step = (pan.x / 12f).toInt()
                        if (step != 0) {
                            val lower = minOf(visibleCount.toInt(), dataSize)
                            endIndex = (endIndex - step).coerceIn(lower, dataSize)
                        }
                        // 拖动或缩放时取消十字光标
                        if (step != 0 || zoom != 1f) crosshairIdx = null
                    } catch (_: Exception) { }
                }
            }
            .pointerInput(dataSize) {
                detectTapGestures { offset ->
                    if (view.isEmpty()) return@detectTapGestures
                    val slot = size.width / view.size
                    val idx = (offset.x / slot).toInt().coerceIn(0, view.size - 1)
                    val fullIdx = start + idx
                    crosshairIdx = if (crosshairIdx == fullIdx) null else fullIdx
                    crosshairY = offset.y.coerceIn(0f, size.height.toFloat())
                }
            }
    ) {
        if (view.isEmpty()) return@Canvas
        val h = size.height
        val w = size.width
        if (w <= 0f || h <= 0f) return@Canvas
        val priceTop = 0f
        val priceBottom = h * 0.50f
        val volumeTop = h * 0.50f
        val volumeBottom = h * 0.63f
        val indTop = h * 0.65f
        val indBottom = h * 0.96f

        // 价格范围
        var hi = view.maxOf { it.high }
        var lo = view.minOf { it.low }
        if (mode == IndicatorMode.BOLL && indicator.bollUp != null) {
            val n = (end - 1).coerceIn(0, (indicator.bollUp?.size ?: 1) - 1)
            indicator.bollUp?.getOrNull(n)?.takeIf { !it.isNaN() }?.let { hi = max(hi, it) }
            indicator.bollLow?.getOrNull(n)?.takeIf { !it.isNaN() }?.let { lo = min(lo, it) }
        }
        val pad = (hi - lo) * 0.06
        hi += pad; lo -= pad
        if (hi <= lo) { hi = lo + 1.0 }

        fun priceY(p: Double): Float = priceBottom - ((p - lo) / (hi - lo) * (priceBottom - priceTop)).toFloat()

        // 网格 + 价格标签
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = priceTop + (priceBottom - priceTop) * i / gridLines
            drawLine(BgGrid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            val price = hi - (hi - lo) * i / gridLines
            val layout = textMeasurer.measure("%.2f".format(price), TextStyleSmall)
            drawText(layout, topLeft = Offset(4f, y - layout.size.height / 2f))
        }

        // 蜡烛
        val slot = w / view.size
        val bodyW = (slot * 0.65f).coerceIn(1f, 40f)
        view.forEachIndexed { i, k ->
            val cx = slot * i + slot / 2
            val up = k.close >= k.open
            val color = if (up) DsaRed else DsaGreen
            val yOpen = priceY(k.open)
            val yClose = priceY(k.close)
            val yHigh = priceY(k.high)
            val yLow = priceY(k.low)
            drawLine(color, Offset(cx, yHigh), Offset(cx, yLow), strokeWidth = max(1f, bodyW * 0.15f))
            val top = min(yOpen, yClose)
            val bottom = max(yOpen, yClose)
            val bodyH = max(1f, bottom - top)
            drawRect(color, Offset(cx - bodyW / 2, top), size = androidx.compose.ui.geometry.Size(bodyW, bodyH))
        }

        // 最新价标记线
        val lastPrice = view.last().close
        val lastY = priceY(lastPrice)
        val lastColor = if (lastPrice >= view.first().open) DsaRed else DsaGreen
        drawLine(
            lastColor.copy(alpha = 0.5f), Offset(0f, lastY), Offset(w, lastY),
            strokeWidth = 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
        )
        val lastLabel = textMeasurer.measure("%.2f".format(lastPrice), TextStyleValue.copy(color = Color.White))
        drawRect(lastColor, Offset(w - lastLabel.size.width - 6f, lastY - lastLabel.size.height / 2f - 2f),
            size = androidx.compose.ui.geometry.Size(lastLabel.size.width + 6f, lastLabel.size.height + 4f))
        drawText(lastLabel, topLeft = Offset(w - lastLabel.size.width - 3f, lastY - lastLabel.size.height / 2f))

        // 主图指标线
        if (mode == IndicatorMode.MA || mode == IndicatorMode.BOLL) {
            drawIndicatorLine(view, indicator.ma5, start, Ma5Color, ::priceY)
            drawIndicatorLine(view, indicator.ma10, start, Ma10Color, ::priceY)
            drawIndicatorLine(view, indicator.ma20, start, Ma20Color, ::priceY)
        }
        if (mode == IndicatorMode.BOLL) {
            drawIndicatorLine(view, indicator.bollUp, start, Ma10Color, ::priceY, dash = true)
            drawIndicatorLine(view, indicator.bollLow, start, Ma20Color, ::priceY, dash = true)
        }

        // 成交量
        val maxVol = view.maxOf { it.volume }.toFloat().coerceAtLeast(1f)
        view.forEachIndexed { i, k ->
            val cx = slot * i + slot / 2
            val hgt = ((k.volume / maxVol) * (volumeBottom - volumeTop)).toFloat()
            val up = k.close >= k.open
            drawRect(
                if (up) DsaRed.copy(alpha = 0.7f) else DsaGreen.copy(alpha = 0.7f),
                Offset(cx - bodyW / 2, volumeBottom - hgt),
                size = androidx.compose.ui.geometry.Size(bodyW, hgt),
            )
        }
        drawLine(BgGrid, Offset(0f, volumeBottom), Offset(w, volumeBottom), strokeWidth = 1f)

        // 副图指标
        drawIndicatorPanel(view, indicator, mode, indTop, indBottom, slot, textMeasurer, start)

        // 十字光标
        crosshairIdx?.let { cIdx ->
            if (cIdx < start || cIdx >= end) return@let
            val relIdx = cIdx - start
            val cx = slot * relIdx + slot / 2
            val k = view[relIdx]
            // 垂直线（贯穿主图+成交量+副图）
            drawLine(CrosshairColor, Offset(cx, priceTop), Offset(cx, indBottom), strokeWidth = 1f)
            // 水平线（主图区域，跟随手指Y）
            val cy = crosshairY.coerceIn(priceTop, priceBottom)
            drawLine(CrosshairColor, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)
            // 水平价格标签
            val crossPrice = lo + (hi - lo) * (priceBottom - cy) / (priceBottom - priceTop)
            val cpLabel = textMeasurer.measure("%.2f".format(crossPrice), TextStyleValue.copy(color = Color.White))
            drawRect(CrosshairColor, Offset(0f, cy - cpLabel.size.height / 2f - 2f),
                size = androidx.compose.ui.geometry.Size(cpLabel.size.width + 6f, cpLabel.size.height + 4f))
            drawText(cpLabel, topLeft = Offset(3f, cy - cpLabel.size.height / 2f))
            // 顶部 OHLC 浮窗
            val ohlcText = "${k.day}  开${"%.2f".format(k.open)}  高${"%.2f".format(k.high)}  低${"%.2f".format(k.low)}  收${"%.2f".format(k.close)}  量${"%.0f".format(k.volume)}"
            val ohlcLabel = textMeasurer.measure(ohlcText, TextStyleValue.copy(color = Color.White))
            val boxW = min(ohlcLabel.size.width + 12f, w - 8f)
            val boxX = (cx - boxW / 2f).coerceIn(4f, w - boxW - 4f)
            drawRect(Color(0xDD000000), Offset(boxX, priceTop + 2f),
                size = androidx.compose.ui.geometry.Size(boxW, ohlcLabel.size.height + 8f))
            drawText(ohlcLabel, topLeft = Offset(boxX + 6f, priceTop + 6f))
            // 底部日期标签
            val dateLabel = textMeasurer.measure(k.day, TextStyleValue.copy(color = Color.White))
            drawRect(CrosshairColor, Offset(cx - dateLabel.size.width / 2f - 4f, h - dateLabel.size.height - 4f),
                size = androidx.compose.ui.geometry.Size(dateLabel.size.width + 8f, dateLabel.size.height + 4f))
            drawText(dateLabel, topLeft = Offset(cx - dateLabel.size.width / 2f, h - dateLabel.size.height - 2f))
        }

        // 底部日期（无十字光标时）
        if (crosshairIdx == null) {
            val dateLayout = textMeasurer.measure(view.first().day, TextStyleSmall)
            drawText(dateLayout, topLeft = Offset(4f, h - dateLayout.size.height - 2f))
            val lastLayout = textMeasurer.measure(view.last().day, TextStyleSmall)
            drawText(lastLayout, topLeft = Offset(w - lastLayout.size.width - 4f, h - lastLayout.size.height - 2f))
        }
    }
}

private fun DrawScope.drawIndicatorLine(
    view: List<KlinePoint>,
    values: DoubleArray?,
    startIdx: Int,
    color: Color,
    priceY: (Double) -> Float,
    dash: Boolean = false,
) {
    if (values == null) return
    val path = Path()
    var started = false
    view.forEachIndexed { i, _ ->
        val idx = startIdx + i
        if (idx < 0 || idx >= values.size) return@forEachIndexed
        val v = values[idx]
        if (v.isNaN()) return@forEachIndexed
        val x = size.width * i / view.size + size.width / view.size / 2
        val y = priceY(v)
        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
    }
    if (started) {
        val style = if (dash)
            Stroke(width = 1.2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
        else Stroke(width = 1.2f)
        drawPath(path, color, style = style)
    }
}

private fun DrawScope.drawIndicatorPanel(
    view: List<KlinePoint>,
    ind: IndicatorResult,
    mode: IndicatorMode,
    top: Float,
    bottom: Float,
    slot: Float,
    tm: androidx.compose.ui.text.TextMeasurer,
    startIdx: Int,
) {
    if (view.isEmpty()) return
    drawLine(BgGrid, Offset(0f, top), Offset(size.width, top), strokeWidth = 1f)
    drawLine(BgGrid, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1f)

    fun slotX(i: Int): Float = slot * i + slot / 2
    fun yFor(value: Double, minV: Double, maxV: Double): Float =
        bottom - ((value - minV) / (if (maxV > minV) maxV - minV else 1.0) * (bottom - top)).toFloat()
    fun fmt(v: Double?) = if (v == null || v.isNaN()) "—" else "%.2f".format(v)

    val n = (startIdx + view.size - 1).coerceIn(0, (ind.dif?.size ?: 1) - 1)

    // 指标名称 + 最新数值标签
    val labelAndValues = when (mode) {
        IndicatorMode.MACD -> "MACD(12,26,9)  DIF:${fmt(ind.dif?.getOrNull(n))}  DEA:${fmt(ind.dea?.getOrNull(n))}  柱:${fmt(ind.macd?.getOrNull(n))}"
        IndicatorMode.KDJ -> "KDJ(9,3,3)  K:${fmt(ind.k?.getOrNull(n))}  D:${fmt(ind.d?.getOrNull(n))}  J:${fmt(ind.j?.getOrNull(n))}"
        IndicatorMode.RSI -> "RSI  RSI6:${fmt(ind.rsi6?.getOrNull(n))}  RSI12:${fmt(ind.rsi12?.getOrNull(n))}  RSI24:${fmt(ind.rsi24?.getOrNull(n))}"
        IndicatorMode.WR -> "WR(14)  WR:${fmt(ind.wr?.getOrNull(n))}"
        IndicatorMode.CCI -> "CCI(14)  CCI:${fmt(ind.cci?.getOrNull(n))}"
        IndicatorMode.DMI -> "DMI(14)  ADX:${fmt(ind.adx?.getOrNull(n))}  +DI:${fmt(ind.pdi?.getOrNull(n))}  -DI:${fmt(ind.mdi?.getOrNull(n))}"
        IndicatorMode.BIAS -> "BIAS  BIAS6:${fmt(ind.bias6?.getOrNull(n))}  BIAS12:${fmt(ind.bias12?.getOrNull(n))}"
        IndicatorMode.OBV -> "OBV  能量潮:${fmt(ind.obv?.getOrNull(n))}"
        IndicatorMode.ROC -> "ROC(12)  ROC:${fmt(ind.roc?.getOrNull(n))}"
        IndicatorMode.ATR -> "ATR(14)  ATR:${fmt(ind.atr?.getOrNull(n))}"
        IndicatorMode.STOCH -> "KD随机(14,3)  K:${fmt(ind.stochK?.getOrNull(n))}  D:${fmt(ind.stochD?.getOrNull(n))}"
        IndicatorMode.MFI -> "MFI(14)  资金流量:${fmt(ind.mfi?.getOrNull(n))}"
        IndicatorMode.VWAP -> "VWAP  成交量加权均价:${fmt(ind.vwap?.getOrNull(n))}"
        IndicatorMode.TSI -> "TSI  真实强度:${fmt(ind.tsi?.getOrNull(n))}"
        IndicatorMode.TRIX -> "TRIX(15)  三重指数:${fmt(ind.trix?.getOrNull(n))}"
        IndicatorMode.ULTIMATE -> "UO(7,14,28)  终极振荡:${fmt(ind.ultimate?.getOrNull(n))}"
        IndicatorMode.AO -> "AO  动量震荡:${fmt(ind.ao?.getOrNull(n))}"
        IndicatorMode.CMF -> "CMF(20)  佳庆资金流:${fmt(ind.cmf?.getOrNull(n))}"
        IndicatorMode.KELTNER -> "肯特纳通道(20,2)"
        IndicatorMode.DONCHIAN -> "唐奇安通道(20)"
        IndicatorMode.SUPERTREND -> "超级趋势(10,3)  ${if ((ind.superTrendDir?.getOrNull(n) ?: 0.0) > 0) "多头" else "空头"}"
        IndicatorMode.ICHIMOKU -> "一目均衡表  转换线:${fmt(ind.ichimokuTenkan?.getOrNull(n))}  基准线:${fmt(ind.ichimokuKijun?.getOrNull(n))}"
        IndicatorMode.RVI -> "RVI(10)  相对活力:${fmt(ind.rvi?.getOrNull(n))}"
        IndicatorMode.COPPOCK -> "Coppock  科普曲线:${fmt(ind.coppock?.getOrNull(n))}"
        IndicatorMode.PPO -> "PPO(12,26)  百分比价格振荡:${fmt(ind.ppo?.getOrNull(n))}"
        IndicatorMode.QSTICK -> "Qstick(10):${fmt(ind.qstick?.getOrNull(n))}"
        IndicatorMode.FISHER -> "Fisher变换(10):${fmt(ind.fisher?.getOrNull(n))}"
        else -> ""
    }
    if (labelAndValues.isNotEmpty()) {
        val layout = tm.measure(labelAndValues, TextStyleSmall)
        drawText(layout, topLeft = Offset(4f, top + 2f))
    }

    // 数据不足提示
    val minData = when (mode) {
        IndicatorMode.MACD -> 26
        IndicatorMode.KDJ -> 9
        IndicatorMode.RSI -> 7
        IndicatorMode.WR -> 14
        IndicatorMode.CCI -> 14
        IndicatorMode.DMI -> 28
        IndicatorMode.BIAS -> 6
        IndicatorMode.ATR -> 14
        IndicatorMode.STOCH -> 14
        IndicatorMode.MFI -> 14
        IndicatorMode.TSI -> 38
        IndicatorMode.TRIX -> 45
        IndicatorMode.ULTIMATE -> 28
        IndicatorMode.AO -> 34
        IndicatorMode.CMF -> 20
        IndicatorMode.KELTNER -> 20
        IndicatorMode.DONCHIAN -> 20
        IndicatorMode.SUPERTREND -> 10
        IndicatorMode.ICHIMOKU -> 52
        IndicatorMode.RVI -> 10
        IndicatorMode.COPPOCK -> 24
        IndicatorMode.PPO -> 26
        IndicatorMode.QSTICK -> 10
        IndicatorMode.FISHER -> 10
        else -> 0
    }
    if (minData > 0 && view.size < minData) {
        val tip = tm.measure("数据不足（需${minData}根，当前${view.size}根），切换日K查看", TextStyleSmall)
        drawText(tip, topLeft = Offset(size.width / 2 - tip.size.width / 2, (top + bottom) / 2 - tip.size.height / 2))
        return
    }

    when (mode) {
        IndicatorMode.MACD -> {
            val dif = ind.dif ?: return
            val dea = ind.dea ?: return
            val macd = ind.macd ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= dif.size) continue
                maxAbs = max(maxAbs, abs(dif[i]).toFloat())
                maxAbs = max(maxAbs, abs(dea[i]).toFloat())
                maxAbs = max(maxAbs, abs(macd[i]).toFloat())
            }
            val midY = (top + bottom) / 2
            view.forEachIndexed { i, _ ->
                val idx = startIdx + i
                if (idx < 0 || idx >= macd.size) return@forEachIndexed
                val v = macd[idx]
                if (v.isNaN()) return@forEachIndexed
                val h = (v / maxAbs * (bottom - top) / 2).toFloat()
                val color = if (v >= 0) DsaRed.copy(alpha = 0.8f) else DsaGreen.copy(alpha = 0.8f)
                drawRect(color, Offset(slotX(i) - slot * 0.25f, if (v >= 0) midY - h else midY), size = androidx.compose.ui.geometry.Size(slot * 0.5f, abs(h)))
            }
            drawLineSeries(view, dif, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLineSeries(view, dea, startIdx, Ma10Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
        }
        IndicatorMode.KDJ -> {
            val kk = ind.k ?: return
            val dd = ind.d ?: return
            val jj = ind.j ?: return
            drawLineSeries(view, kk, startIdx, Ma5Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLineSeries(view, dd, startIdx, Ma10Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLineSeries(view, jj, startIdx, Ma20Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLine(BgGrid, Offset(0f, yFor(20.0, 0.0, 100.0)), Offset(size.width, yFor(20.0, 0.0, 100.0)), strokeWidth = 1f)
            drawLine(BgGrid, Offset(0f, yFor(80.0, 0.0, 100.0)), Offset(size.width, yFor(80.0, 0.0, 100.0)), strokeWidth = 1f)
        }
        IndicatorMode.RSI -> {
            val r6 = ind.rsi6 ?: return
            val r12 = ind.rsi12 ?: return
            val r24 = ind.rsi24 ?: return
            drawLineSeries(view, r6, startIdx, Ma5Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLineSeries(view, r12, startIdx, Ma10Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLineSeries(view, r24, startIdx, Ma20Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLine(BgGrid, Offset(0f, yFor(30.0, 0.0, 100.0)), Offset(size.width, yFor(30.0, 0.0, 100.0)), strokeWidth = 1f)
            drawLine(BgGrid, Offset(0f, yFor(70.0, 0.0, 100.0)), Offset(size.width, yFor(70.0, 0.0, 100.0)), strokeWidth = 1f)
        }
        IndicatorMode.WR -> {
            val wr = ind.wr ?: return
            drawLineSeries(view, wr, startIdx, Ma5Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLine(BgGrid, Offset(0f, yFor(20.0, 0.0, 100.0)), Offset(size.width, yFor(20.0, 0.0, 100.0)), strokeWidth = 1f)
            drawLine(BgGrid, Offset(0f, yFor(80.0, 0.0, 100.0)), Offset(size.width, yFor(80.0, 0.0, 100.0)), strokeWidth = 1f)
        }
        IndicatorMode.CCI -> {
            val cci = ind.cci ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= cci.size) continue
                if (!cci[i].isNaN()) maxAbs = max(maxAbs, abs(cci[i]).toFloat())
            }
            drawLineSeries(view, cci, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(100.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(100.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
            drawLine(BgGrid, Offset(0f, yFor(-100.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(-100.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.DMI -> {
            val adx = ind.adx ?: return
            val pdi = ind.pdi ?: return
            val mdi = ind.mdi ?: return
            drawLineSeries(view, adx, startIdx, Ma5Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLineSeries(view, pdi, startIdx, DsaRed, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLineSeries(view, mdi, startIdx, DsaGreen, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLine(BgGrid, Offset(0f, yFor(25.0, 0.0, 100.0)), Offset(size.width, yFor(25.0, 0.0, 100.0)), strokeWidth = 1f)
        }
        IndicatorMode.BIAS -> {
            val b6 = ind.bias6 ?: return
            val b12 = ind.bias12 ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= b6.size) continue
                if (!b6[i].isNaN()) maxAbs = max(maxAbs, abs(b6[i]).toFloat())
                if (i < b12.size && !b12[i].isNaN()) maxAbs = max(maxAbs, abs(b12[i]).toFloat())
            }
            maxAbs = max(maxAbs, 5f)
            drawLineSeries(view, b6, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLineSeries(view, b12, startIdx, Ma10Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.OBV -> {
            val obv = ind.obv ?: return
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= obv.size) continue
                if (obv[i] < minV) minV = obv[i]
                if (obv[i] > maxV) maxV = obv[i]
            }
            if (minV == maxV) { minV -= 1; maxV += 1 }
            drawLineSeries(view, obv, startIdx, Ma5Color, ::slotX, { yFor(it, minV, maxV) })
        }
        IndicatorMode.ROC -> {
            val roc = ind.roc ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= roc.size) continue
                if (!roc[i].isNaN()) maxAbs = max(maxAbs, abs(roc[i]).toFloat())
            }
            maxAbs = max(maxAbs, 5f)
            drawLineSeries(view, roc, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.ATR -> {
            val atr = ind.atr ?: return
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= atr.size || atr[i].isNaN()) continue
                if (atr[i] < minV) minV = atr[i]
                if (atr[i] > maxV) maxV = atr[i]
            }
            if (minV == maxV || minV.isInfinite()) { minV = 0.0; maxV = 1.0 }
            drawLineSeries(view, atr, startIdx, Ma5Color, ::slotX, { yFor(it, minV, maxV) })
        }
        IndicatorMode.STOCH -> {
            val k = ind.stochK ?: return
            val d = ind.stochD ?: return
            drawLineSeries(view, k, startIdx, Ma5Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLineSeries(view, d, startIdx, Ma10Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLine(BgGrid, Offset(0f, yFor(20.0, 0.0, 100.0)), Offset(size.width, yFor(20.0, 0.0, 100.0)), strokeWidth = 1f)
            drawLine(BgGrid, Offset(0f, yFor(80.0, 0.0, 100.0)), Offset(size.width, yFor(80.0, 0.0, 100.0)), strokeWidth = 1f)
        }
        IndicatorMode.MFI -> {
            val mfi = ind.mfi ?: return
            drawLineSeries(view, mfi, startIdx, Ma5Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLine(BgGrid, Offset(0f, yFor(20.0, 0.0, 100.0)), Offset(size.width, yFor(20.0, 0.0, 100.0)), strokeWidth = 1f)
            drawLine(BgGrid, Offset(0f, yFor(80.0, 0.0, 100.0)), Offset(size.width, yFor(80.0, 0.0, 100.0)), strokeWidth = 1f)
        }
        IndicatorMode.VWAP -> {
            val vwap = ind.vwap ?: return
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= vwap.size || vwap[i].isNaN()) continue
                if (vwap[i] < minV) minV = vwap[i]
                if (vwap[i] > maxV) maxV = vwap[i]
            }
            if (minV == maxV || minV.isInfinite()) { minV = 0.0; maxV = 1.0 }
            drawLineSeries(view, vwap, startIdx, Ma5Color, ::slotX, { yFor(it, minV, maxV) })
        }
        IndicatorMode.TSI -> {
            val tsi = ind.tsi ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= tsi.size || tsi[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(tsi[i]).toFloat())
            }
            maxAbs = max(maxAbs, 25f)
            drawLineSeries(view, tsi, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.TRIX -> {
            val trix = ind.trix ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= trix.size || trix[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(trix[i]).toFloat())
            }
            maxAbs = max(maxAbs, 1f)
            drawLineSeries(view, trix, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.ULTIMATE -> {
            val uo = ind.ultimate ?: return
            drawLineSeries(view, uo, startIdx, Ma5Color, ::slotX, { yFor(it, 0.0, 100.0) })
            drawLine(BgGrid, Offset(0f, yFor(30.0, 0.0, 100.0)), Offset(size.width, yFor(30.0, 0.0, 100.0)), strokeWidth = 1f)
            drawLine(BgGrid, Offset(0f, yFor(70.0, 0.0, 100.0)), Offset(size.width, yFor(70.0, 0.0, 100.0)), strokeWidth = 1f)
        }
        IndicatorMode.AO -> {
            val ao = ind.ao ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= ao.size || ao[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(ao[i]).toFloat())
            }
            val midY = (top + bottom) / 2
            view.forEachIndexed { i, _ ->
                val idx = startIdx + i
                if (idx < 0 || idx >= ao.size) return@forEachIndexed
                val v = ao[idx]
                if (v.isNaN()) return@forEachIndexed
                val h = (v / maxAbs * (bottom - top) / 2).toFloat()
                val color = if (v >= 0) DsaRed.copy(alpha = 0.8f) else DsaGreen.copy(alpha = 0.8f)
                drawRect(color, Offset(slotX(i) - slot * 0.25f, if (v >= 0) midY - h else midY), size = androidx.compose.ui.geometry.Size(slot * 0.5f, abs(h)))
            }
        }
        IndicatorMode.CMF -> {
            val cmf = ind.cmf ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= cmf.size || cmf[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(cmf[i]).toFloat())
            }
            maxAbs = max(maxAbs, 0.3f)
            drawLineSeries(view, cmf, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.KELTNER -> {
            val mid = ind.keltnerMid ?: return
            val up = ind.keltnerUp ?: return
            val low = ind.keltnerLow ?: return
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= up.size) continue
                if (!up[i].isNaN() && up[i] > maxV) maxV = up[i]
                if (!low[i].isNaN() && low[i] < minV) minV = low[i]
            }
            if (minV == maxV || minV.isInfinite()) { minV = 0.0; maxV = 1.0 }
            drawLineSeries(view, up, startIdx, DsaRed.copy(alpha = 0.6f), ::slotX, { yFor(it, minV, maxV) })
            drawLineSeries(view, mid, startIdx, Ma10Color, ::slotX, { yFor(it, minV, maxV) })
            drawLineSeries(view, low, startIdx, DsaGreen.copy(alpha = 0.6f), ::slotX, { yFor(it, minV, maxV) })
        }
        IndicatorMode.DONCHIAN -> {
            val up = ind.donchianUp ?: return
            val low = ind.donchianLow ?: return
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= up.size) continue
                if (!up[i].isNaN() && up[i] > maxV) maxV = up[i]
                if (!low[i].isNaN() && low[i] < minV) minV = low[i]
            }
            if (minV == maxV || minV.isInfinite()) { minV = 0.0; maxV = 1.0 }
            drawLineSeries(view, up, startIdx, DsaRed.copy(alpha = 0.6f), ::slotX, { yFor(it, minV, maxV) })
            drawLineSeries(view, low, startIdx, DsaGreen.copy(alpha = 0.6f), ::slotX, { yFor(it, minV, maxV) })
        }
        IndicatorMode.SUPERTREND -> {
            val st = ind.superTrend ?: return
            val dir = ind.superTrendDir ?: return
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= st.size || st[i].isNaN()) continue
                if (st[i] < minV) minV = st[i]
                if (st[i] > maxV) maxV = st[i]
            }
            if (minV == maxV || minV.isInfinite()) { minV = 0.0; maxV = 1.0 }
            // 多头绿色，空头红色
            view.forEachIndexed { i, _ ->
                val idx = startIdx + i
                if (idx < 1 || idx >= st.size || idx >= dir.size) return@forEachIndexed
                if (st[idx].isNaN() || dir[idx].isNaN()) return@forEachIndexed
                val color = if (dir[idx] > 0) DsaGreen else DsaRed
                drawLine(color, Offset(slotX(i - 1), yFor(st[idx - 1], minV, maxV)), Offset(slotX(i), yFor(st[idx], minV, maxV)), strokeWidth = 2f)
            }
        }
        IndicatorMode.ICHIMOKU -> {
            val tenkan = ind.ichimokuTenkan ?: return
            val kijun = ind.ichimokuKijun ?: return
            val senkouA = ind.ichimokuSenkouA ?: return
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= tenkan.size) continue
                listOf(tenkan[i], kijun[i], senkouA[i]).forEach { v ->
                    if (!v.isNaN()) {
                        if (v < minV) minV = v
                        if (v > maxV) maxV = v
                    }
                }
            }
            if (minV == maxV || minV.isInfinite()) { minV = 0.0; maxV = 1.0 }
            drawLineSeries(view, tenkan, startIdx, Ma5Color, ::slotX, { yFor(it, minV, maxV) })
            drawLineSeries(view, kijun, startIdx, Ma10Color, ::slotX, { yFor(it, minV, maxV) })
            drawLineSeries(view, senkouA, startIdx, DsaRed.copy(alpha = 0.5f), ::slotX, { yFor(it, minV, maxV) })
        }
        IndicatorMode.RVI -> {
            val rvi = ind.rvi ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= rvi.size || rvi[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(rvi[i]).toFloat())
            }
            maxAbs = max(maxAbs, 50f)
            drawLineSeries(view, rvi, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.COPPOCK -> {
            val coppock = ind.coppock ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= coppock.size || coppock[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(coppock[i]).toFloat())
            }
            maxAbs = max(maxAbs, 10f)
            drawLineSeries(view, coppock, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.PPO -> {
            val ppo = ind.ppo ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= ppo.size || ppo[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(ppo[i]).toFloat())
            }
            maxAbs = max(maxAbs, 5f)
            drawLineSeries(view, ppo, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.QSTICK -> {
            val qstick = ind.qstick ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= qstick.size || qstick[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(qstick[i]).toFloat())
            }
            drawLineSeries(view, qstick, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        IndicatorMode.FISHER -> {
            val fisher = ind.fisher ?: return
            var maxAbs = 0.0001f
            for (i in startIdx until startIdx + view.size) {
                if (i < 0 || i >= fisher.size || fisher[i].isNaN()) continue
                maxAbs = max(maxAbs, abs(fisher[i]).toFloat())
            }
            maxAbs = max(maxAbs, 5f)
            drawLineSeries(view, fisher, startIdx, Ma5Color, ::slotX, { yFor(it, -maxAbs.toDouble(), maxAbs.toDouble()) })
            drawLine(BgGrid, Offset(0f, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), Offset(size.width, yFor(0.0, -maxAbs.toDouble(), maxAbs.toDouble())), strokeWidth = 1f)
        }
        else -> Unit
    }
}

private fun DrawScope.drawLineSeries(
    view: List<KlinePoint>,
    values: DoubleArray,
    startIdx: Int,
    color: Color,
    x: (Int) -> Float,
    y: (Double) -> Float,
) {
    val path = Path()
    var started = false
    view.forEachIndexed { i, _ ->
        val idx = startIdx + i
        if (idx < 0 || idx >= values.size) return@forEachIndexed
        val v = values[idx]
        if (v.isNaN()) return@forEachIndexed
        val px = x(i)
        val py = y(v)
        if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
    }
    if (started) drawPath(path, color, style = Stroke(width = 1.2f))
}
