package com.dsa.app.ui

import com.dsa.app.util.Fmt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.analysis.Indicators
import com.dsa.app.data.*
import com.dsa.app.ui.chart.CandlestickChart
import com.dsa.app.ui.chart.IndicatorMode
import com.dsa.app.ui.theme.DsaGreen
import com.dsa.app.ui.theme.DsaRed

private fun fmtD(v: Double): String = if (v == 0.0) "—" else Fmt.d(v)

@Composable
fun DetailScreen(
    vm: SharedViewModel,
    code: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var quote by remember { mutableStateOf<Quote?>(null) }
    var kline by remember { mutableStateOf<List<KlinePoint>>(emptyList()) }
    var indicator by remember { mutableStateOf(IndicatorResult()) }
    var period by remember { mutableStateOf("day") }
    var mode by remember { mutableStateOf(IndicatorMode.MA) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var requestText by remember { mutableStateOf("") }
    var showAiHistory by remember { mutableStateOf(false) }
    var savedFlag by remember { mutableStateOf(false) }

    // 切换股票时清空上一只股票的 AI 分析状态，避免报告串股
    LaunchedEffect(code) { vm.resetAiState() }

    LaunchedEffect(code, period, retryCount) {
        try {
            val useThs = vm.dataSource == "ths" && vm.thsApiKey.isNotBlank() && !vm.dataSourceFallback
            val q = if (useThs) {
                try {
                    val quotes = com.dsa.app.data.ThsApi.fetchQuotes(listOf(code), vm.thsApiKey)
                    val names = com.dsa.app.data.ThsApi.fetchNames(listOf(code), vm.thsApiKey)
                    quotes.firstOrNull()?.let {
                        if (it.name == it.code && names.containsKey(code)) it.copy(name = names[code] ?: it.name) else it
                    } ?: MarketApi.fetchQuote(code)
                } catch (e: Exception) {
                    vm.markDataSourceFallback()
                    MarketApi.fetchQuote(code)
                }
            } else {
                MarketApi.fetchQuote(code)
            }
            val kl = if (useThs && period == "day") {
                try {
                    com.dsa.app.data.ThsApi.fetchKline(code, 320, vm.thsApiKey)
                } catch (e: Exception) {
                    vm.markDataSourceFallback()
                    MarketApi.fetchKline(code, period, 320)
                }
            } else {
                MarketApi.fetchKline(code, period, 320)
            }
            quote = q
            kline = kl
            indicator = Indicators.computeAll(kl)
        } catch (e: Exception) {
            error = e.message ?: "数据加载失败"
        } finally {
            loading = false
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        quote?.name ?: "加载中…",
                        color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        quote?.let { "${it.code}  ${it.time}" } ?: code,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), fontSize = 12.sp,
                    )
                }
                // 添加自选 / 持仓
                val norm = vm.normalizeCode(code)
                val inWatch = vm.watchlist.any { vm.normalizeCode(it) == norm }
                val inHolding = vm.holdings.any { vm.normalizeCode(it.code) == norm }
                if (!inWatch) {
                    TextButton(onClick = { vm.addStockAndRefresh(code) }) {
                        Text("+自选", color = Color.White, fontSize = 13.sp)
                    }
                }
                if (!inHolding) {
                    TextButton(onClick = {
                        vm.addHoldingsAndRefresh(listOf(Holding(code = norm)))
                        showToast("已加入持仓，可到持仓页编辑数量/成本")
                    }) {
                        Text("+持仓", color = Color.White, fontSize = 13.sp)
                    }
                }
                // 周期切换
                listOf("day" to "日K", "week" to "周K", "month" to "月K").forEach { (p, label) ->
                    TextButton(onClick = { period = p }) {
                        Text(label, color = if (period == p) Color.White else Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null && kline.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(error ?: "加载失败", color = DsaRed, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { retryCount++ }) { Text("重试") }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                ) {
                    // 行情头部
                    quote?.let { q ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.d(q.price), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                color = if (q.isUp) DsaRed else DsaGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(Fmt.s(q.change) + "  " + Fmt.s(q.changePct) + "%", fontSize = 16.sp,
                                color = if (q.isUp) DsaRed else DsaGreen)
                            if (q.isLimitUp) {
                                Spacer(Modifier.width(8.dp))
                                Text("涨停", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DsaRed,
                                    modifier = Modifier.background(DsaRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            if (q.price <= q.limitDown) {
                                Spacer(Modifier.width(8.dp))
                                Text("跌停", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DsaGreen,
                                    modifier = Modifier.background(DsaGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            InfoItem("今开", fmtD(q.open)); InfoItem("最高", fmtD(q.high)); InfoItem("最低", fmtD(q.low))
                            InfoItem("昨收", fmtD(q.prevClose))
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            InfoItem("成交量", "${q.volume}手"); InfoItem("成交额", Fmt.d(q.amount, 0) + "万")
                            InfoItem("换手", Fmt.d(q.turnover) + "%")
                            InfoItem("振幅", Fmt.d(q.amplitude) + "%")
                        }
                        if (q.pe > 0) {
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                InfoItem("市盈率", Fmt.d(q.pe)); InfoItem("市净率", Fmt.d(q.pb))
                                InfoItem("总市值", Fmt.d(q.marketCap, 0) + "亿"); InfoItem("流通市值", Fmt.d(q.floatCap, 0) + "亿")
                            }
                        }
                    }

                    // 指标选择
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IndicatorSelector(mode) { mode = it }
                    }

                    // K 线图
                    if (kline.isNotEmpty()) {
                        CandlestickChart(
                            kline = kline,
                            indicator = indicator,
                            mode = mode,
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                        )
                    }

                    // 技术指标摘要
                    if (kline.isNotEmpty()) {
                        IndicatorSummary(kline, indicator)
                    }

                    // 确定性技术信号（本地规则引擎，先于 AI 给出可解释信号）
                    if (kline.isNotEmpty()) {
                        val signals = remember(quote, indicator, kline) {
                            com.dsa.app.analysis.SignalRules.analyze(indicator, quote, kline)
                        }
                        if (signals.isNotEmpty()) {
                            Card(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("技术信号（本地规则引擎）", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    val bullish = signals.count { it.level == com.dsa.app.analysis.SignalRules.Level.BULLISH }
                                    val bearish = signals.count { it.level == com.dsa.app.analysis.SignalRules.Level.BEARISH }
                                    Text(
                                        "共 ${signals.size} 条 · 看多 $bullish · 看空 $bearish",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    signals.forEach { s ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text(
                                                when (s.level) {
                                                    com.dsa.app.analysis.SignalRules.Level.BULLISH -> "▲"
                                                    com.dsa.app.analysis.SignalRules.Level.BEARISH -> "▼"
                                                    else -> "·"
                                                },
                                                fontSize = 12.sp,
                                                color = when (s.level) {
                                                    com.dsa.app.analysis.SignalRules.Level.BULLISH -> DsaRed
                                                    com.dsa.app.analysis.SignalRules.Level.BEARISH -> DsaGreen
                                                    else -> MaterialTheme.colorScheme.outline
                                                },
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "[${s.name}] ${s.text}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                    }

                    // AI 分析
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("AI 智能分析", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showAiHistory = true }) { Text("历史", fontSize = 12.sp) }
                                Button(
                                    onClick = {
                                        savedFlag = false
                                        vm.generateAiReport(code, requestText)
                                    },
                                    enabled = !vm.aiLoading,
                                ) { Text(if (vm.aiLoading) "分析中…" else "生成报告") }
                            }
                            OutlinedTextField(
                                value = requestText,
                                onValueChange = { requestText = it },
                                placeholder = { Text("可选：输入你的问题，如「这只股票适合加仓吗？」", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                maxLines = 2,
                            )
                            if (vm.aiLoading) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("AI 分析中，请稍候…", fontSize = 12.sp)
                                }
                            }
                            vm.aiError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = DsaRed, fontSize = 12.sp)
                            }
                            vm.aiReport?.let {
                                Spacer(Modifier.height(8.dp))
                                if (savedFlag) Text("✓ 已保存到历史", color = DsaGreen, fontSize = 12.sp)
                                Text(it, fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        // 报告在生成时已自动保存到历史
                                        savedFlag = true
                                        showToast("已保存到历史")
                                    }) { Text("保存", fontSize = 12.sp) }
                                    TextButton(onClick = {
                                        shareText("${quote?.name ?: code} - AI 分析报告", it)
                                    }) { Text("导出", fontSize = 12.sp) }
                                    TextButton(onClick = {
                                        savedFlag = false
                                        vm.generateAiReport(code, requestText)
                                    }) { Text("重新分析", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAiHistory) {
        AiReportHistoryDialog(
            vm = vm,
            code = code,
            stockName = quote?.name ?: code,
            onDismiss = { showAiHistory = false },
        )
    }
}

@Composable
private fun AiReportHistoryDialog(
    vm: SharedViewModel,
    code: String,
    stockName: String,
    onDismiss: () -> Unit,
) {
    var viewing by remember { mutableStateOf<AnalysisReport?>(null) }
    val norm = vm.normalizeCode(code)
    val reports = vm.analysisReports.filter { vm.normalizeCode(it.code) == norm }

    if (viewing != null) {
        val r = viewing!!
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("${r.name} - AI 分析报告", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "时间：${Fmt.time(r.createdAt)}  模型：${r.model}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(r.content, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        shareText("${r.name} - AI 分析报告", r.content)
                    }) { Text("导出") }
                    TextButton(onClick = {
                        vm.deleteReport(r.id)
                        viewing = null
                        showToast("已删除")
                    }) { Text("删除", color = DsaRed) }
                    TextButton(onClick = { viewing = null }) { Text("返回") }
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$stockName - 历史 AI 分析（${reports.size}）", fontSize = 16.sp) },
        text = {
            if (reports.isEmpty()) {
                Text("暂无历史报告", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        reports.forEach { r ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewing = r }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.time(r.createdAt), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("模型：${r.model}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        r.content.take(60) + (if (r.content.length > 60) "…" else ""),
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1
                                    )
                                }
                                Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reports.isNotEmpty()) {
                    TextButton(onClick = {
                        vm.clearReports()
                        showToast("已清空")
                    }) { Text("清空", color = DsaRed) }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IndicatorSelector(current: IndicatorMode, onSelect: (IndicatorMode) -> Unit) {
    // 显示当前指标 + 点击切换
    var showMenu by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { showMenu = true },
            label = { Text("副图: ${current.name}", fontSize = 12.sp) },
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            IndicatorMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.name, fontSize = 13.sp) },
                    onClick = { onSelect(m); showMenu = false },
                )
            }
        }
    }
}

@Composable
private fun IndicatorSummary(
    kline: List<KlinePoint>,
    ind: IndicatorResult,
) {
    if (kline.isEmpty()) return
    val n = kline.size - 1
    fun fmt(v: Double?) = if (v == null || v.isNaN()) "—" else Fmt.d(v)
    val trend = Indicators.maTrend(ind, n)
    val trendColor = when (trend) {
        "多头排列" -> DsaRed
        "空头排列" -> DsaGreen
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("技术指标（${kline.last().day}）", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("均线: MA5=${fmt(ind.ma5?.get(n))} MA10=${fmt(ind.ma10?.get(n))} MA20=${fmt(ind.ma20?.get(n))}  [$trend]", fontSize = 12.sp, color = trendColor)
            Text("MACD: DIF=${fmt(ind.dif?.get(n))} DEA=${fmt(ind.dea?.get(n))} 柱=${fmt(ind.macd?.get(n))}", fontSize = 12.sp)
            Text("KDJ: K=${fmt(ind.k?.get(n))} D=${fmt(ind.d?.get(n))} J=${fmt(ind.j?.get(n))}", fontSize = 12.sp)
            Text("RSI: 6=${fmt(ind.rsi6?.get(n))} 12=${fmt(ind.rsi12?.get(n))} 24=${fmt(ind.rsi24?.get(n))}", fontSize = 12.sp)
            Text("BOLL: 上=${fmt(ind.bollUp?.get(n))} 中=${fmt(ind.bollMid?.get(n))} 下=${fmt(ind.bollLow?.get(n))}", fontSize = 12.sp)
        }
    }

    // 威科夫量价分析
    val wyckoffResult = remember(kline) {
        com.dsa.app.analysis.WyckoffAnalysis.analyze(kline)
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("威科夫量价分析", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("阶段: ${wyckoffResult.stage}（${wyckoffResult.stageDescription}）", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            wyckoffResult.tradingRange?.let { tr ->
                Text("交易区间: " + Fmt.d(tr.support) + " - " + Fmt.d(tr.resistance), fontSize = 12.sp)
            }
            wyckoffResult.signals.take(3).forEach { s ->
                Text("• ${s.type}: ${s.description}", fontSize = 12.sp)
            }
            if (wyckoffResult.trendAnalysis.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("趋势: ${wyckoffResult.trendAnalysis}", fontSize = 12.sp)
            }
            if (wyckoffResult.volumeAnalysis.isNotBlank()) {
                Text("量能: ${wyckoffResult.volumeAnalysis}", fontSize = 12.sp)
            }
            if (wyckoffResult.suggestion.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("建议: ${wyckoffResult.suggestion}", fontSize = 12.sp, color = DsaRed)
            }
        }
    }
}
