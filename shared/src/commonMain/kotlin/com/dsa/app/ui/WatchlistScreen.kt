package com.dsa.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.data.Holding
import com.dsa.app.data.HoldingSnapshot
import com.dsa.app.data.MarketApi
import com.dsa.app.data.Quote
import com.dsa.app.data.StockSuggestion
import com.dsa.app.ui.theme.DsaGreen
import com.dsa.app.ui.theme.DsaRed
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

private fun fmt(v: Double): String = if (v == 0.0) "—" else "%.2f".format(v)

@Composable
fun WatchlistScreen(
    vm: SharedViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(WatchTab.WATCH) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHolding by remember { mutableStateOf<String?>(null) }
    var showAnalysis by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showChange by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(SortBy.DEFAULT) }

    LaunchedEffect(vm.watchlist, vm.holdings, vm.currentHoldingAccountId) {
        vm.refreshQuotes()
    }

    val rawCodes = when (tab) {
        WatchTab.WATCH -> vm.watchlist.map { vm.normalizeCode(it) }
        WatchTab.HOLDING -> vm.holdings.map { vm.normalizeCode(it.code) }
    }.distinct()
    val codes = when (sortBy) {
        SortBy.DEFAULT -> rawCodes
        SortBy.CHANGE_DESC -> rawCodes.sortedByDescending { vm.quotes[it]?.changePct ?: Double.NEGATIVE_INFINITY }
        SortBy.CHANGE_ASC -> rawCodes.sortedBy { vm.quotes[it]?.changePct ?: Double.POSITIVE_INFINITY }
        SortBy.CODE -> rawCodes.sorted()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 标题栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("股票智能分析", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                if (tab == WatchTab.WATCH) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加自选", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加持仓", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showChange = true }) {
                        Icon(Icons.Filled.Image, "持仓变化", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { vm.refreshQuotes() }) {
                    Icon(Icons.Filled.Refresh, "刷新", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // 账号切换（持仓）
            if (tab == WatchTab.HOLDING) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.accounts.forEach { acc ->
                        FilterChip(
                            selected = vm.currentHoldingAccountId == acc.id,
                            onClick = { vm.switchHoldingAccount(acc.id) },
                            label = { Text(acc.name, fontSize = 12.sp) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { vm.addHoldingAccount("账号${vm.accounts.size + 1}") },
                        label = { Text("+ 新账号", fontSize = 12.sp) },
                    )
                }
            }

            // 排序
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortBy.entries.forEach { s ->
                    FilterChip(
                        selected = sortBy == s,
                        onClick = { sortBy = s },
                        label = { Text(s.label, fontSize = 12.sp) },
                    )
                }
            }

            vm.refreshError?.let { err ->
                Text(
                    err,
                    color = DsaRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
            }

            // 列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tab == WatchTab.HOLDING) {
                    item { HoldingSummaryCard(vm) }
                }
                items(codes, key = { it }) { code ->
                    val q = vm.quotes[code]
                    val holding = vm.holdings.find { vm.normalizeCode(it.code) == code }
                    if (tab == WatchTab.HOLDING && holding != null) {
                        HoldingCard(
                            quote = q,
                            holding = holding,
                            onOpen = { onOpenDetail(code) },
                            onEdit = { editingHolding = code },
                            onRemove = { vm.removeHolding(code) },
                        )
                    } else {
                        StockCard(
                            quote = q,
                            code = code,
                            onOpen = { onOpenDetail(code) },
                            onRemove = { vm.removeStock(code) },
                        )
                    }
                }
                item {
                    if (tab == WatchTab.HOLDING) {
                        Button(
                            onClick = { showAnalysis = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = vm.holdings.isNotEmpty(),
                        ) { Text("AI 组合分析", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = { showHistory = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("历史组合分析 / 变化分析", fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddStockDialog(
            vm = vm,
            tab = tab,
            onDismiss = { showAddDialog = false },
            onAdd = { code, shares, costPrice ->
                if (tab == WatchTab.HOLDING) {
                    vm.addHoldingsAndRefresh(listOf(Holding(code = code, shares = shares, costPrice = costPrice)))
                } else {
                    vm.addStocksAndRefresh(listOf(code))
                }
                showAddDialog = false
            },
        )
    }

    editingHolding?.let { code ->
        val holding = vm.holdings.find { vm.normalizeCode(it.code) == code }
        if (holding != null) {
            HoldingEditDialog(
                holding = holding,
                quote = vm.quotes[code],
                onDismiss = { editingHolding = null },
                onSave = { h -> vm.updateHolding(h); editingHolding = null },
            )
        }
    }

    if (showAnalysis) {
        PortfolioAnalysisDialog(vm, onDismiss = { showAnalysis = false })
    }
    if (showHistory) {
        HistoryDialog(vm, onDismiss = { showHistory = false })
    }
    if (showChange) {
        HoldingChangeDialog(vm, onDismiss = { showChange = false })
    }
}

enum class WatchTab { WATCH, HOLDING }
enum class SortBy(val label: String) { DEFAULT("默认"), CHANGE_DESC("涨幅↓"), CHANGE_ASC("涨幅↑"), CODE("代码") }

@Composable
private fun StockCard(quote: Quote?, code: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val q = quote
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q?.name ?: "加载中…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(code, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    q?.let { "今开 ${fmt(it.open)}  最高 ${fmt(it.high)}  最低 ${fmt(it.low)}" } ?: "…",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                )
            }
            val color = when {
                q == null -> MaterialTheme.colorScheme.outline
                q.isUp -> DsaRed
                else -> DsaGreen
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(q?.let { "%.2f".format(it.price) } ?: "—", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
                Spacer(Modifier.height(2.dp))
                Text(q?.let { "%+.2f  %+.2f%%".format(it.change, it.changePct) } ?: "—", fontSize = 12.sp, color = color)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "移除", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HoldingCard(quote: Quote?, holding: Holding, onOpen: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    val q = quote
    val profitPct = if (holding.costPrice > 0 && q != null) (q.price - holding.costPrice) / holding.costPrice * 100 else 0.0
    val profitColor = when {
        profitPct > 0 -> DsaRed
        profitPct < 0 -> DsaGreen
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q?.name ?: "加载中…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(holding.code, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "持仓 ${holding.shares}股  成本 ${fmt(holding.costPrice)}  浮盈 ${if (profitPct >= 0) "+" else ""}${"%.2f".format(profitPct)}%",
                    fontSize = 12.sp, color = profitColor,
                )
            }
            val color = when {
                q == null -> MaterialTheme.colorScheme.outline
                q.isUp -> DsaRed
                else -> DsaGreen
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(q?.let { "%.2f".format(it.price) } ?: "—", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
                Spacer(Modifier.height(2.dp))
                Text(q?.let { "%+.2f  %+.2f%%".format(it.change, it.changePct) } ?: "—", fontSize = 12.sp, color = color)
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "移除", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HoldingSummaryCard(vm: SharedViewModel) {
    var totalCost = 0.0
    var totalValue = 0.0
    vm.holdings.forEach { h ->
        val q = vm.quotes[vm.normalizeCode(h.code)]
        val price = q?.price ?: 0.0
        totalCost += h.costPrice * h.shares
        totalValue += price * h.shares
    }
    val profit = totalValue - totalCost
    val profitPct = if (totalCost > 0) profit / totalCost * 100 else 0.0
    val color = if (profit >= 0) DsaRed else DsaGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("持仓汇总", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("总市值", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.2f".format(totalValue), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("总盈亏", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%+.2f  (%+.2f%%)".format(profit, profitPct), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
private fun AddStockDialog(
    vm: SharedViewModel,
    tab: WatchTab,
    onDismiss: () -> Unit,
    onAdd: (String, Int, Double) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StockSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var sharesText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }

    val shares = sharesText.toIntOrNull() ?: 0
    val costPrice = costText.toDoubleOrNull() ?: 0.0

    LaunchedEffect(keyword, vm.dataSource, vm.thsApiKey) {
        if (keyword.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        try {
            val useThs = vm.dataSource == "ths" && vm.thsApiKey.isNotBlank() && !vm.dataSourceFallback
            results = if (useThs) {
                try {
                    com.dsa.app.data.ThsApi.search(keyword, vm.thsApiKey, 20).map {
                        StockSuggestion(code = it.code, name = it.name, market = it.market, type = it.type)
                    }
                } catch (e: Exception) {
                    vm.markDataSourceFallback()
                    MarketApi.searchStocks(keyword)
                }
            } else {
                MarketApi.searchStocks(keyword)
            }
        } catch (e: Exception) {
            results = emptyList()
        }
        searching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tab == WatchTab.HOLDING) "添加持仓股" else "添加自选股") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("代码 / 名称 / 拼音，如 300750、宁德时代") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (searching) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("搜索中…", fontSize = 13.sp)
                    }
                } else if (results.isEmpty() && keyword.isNotBlank()) {
                    Text("无匹配结果，可直接点「添加代码」（名称会自动解析）", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    results.take(6).forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val code = if (s.type == "zs") "${s.market}${s.code}" else s.code
                                onAdd(code, shares, costPrice)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("${s.market}${s.code}  ${if (s.type == "zs") "指数" else "股票"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text("+", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (tab == WatchTab.HOLDING) {
                    Spacer(Modifier.height(10.dp))
                    Text("持仓信息（可选，添加后可编辑）", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sharesText,
                            onValueChange = { sharesText = it.filter { c -> c.isDigit() } },
                            label = { Text("持仓数量(股)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("成本价") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(keyword.trim(), shares, costPrice) },
                enabled = keyword.isNotBlank(),
            ) { Text("添加代码") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun HoldingEditDialog(
    holding: Holding,
    quote: Quote?,
    onDismiss: () -> Unit,
    onSave: (Holding) -> Unit,
) {
    var sharesText by remember { mutableStateOf(holding.shares.let { if (it > 0) it.toString() else "" }) }
    var costText by remember { mutableStateOf(holding.costPrice.let { if (it > 0) "%.2f".format(it) else "" }) }
    val shares = sharesText.toIntOrNull() ?: 0
    val cost = costText.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑持仓：${quote?.name ?: holding.code}") },
        text = {
            Column {
                OutlinedTextField(
                    value = sharesText,
                    onValueChange = { sharesText = it.filter { c -> c.isDigit() } },
                    label = { Text("持仓数量(股)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("成本价") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { onSave(holding.copy(shares = holding.shares - 100, costPrice = holding.costPrice)) }) { Text("-100") }
                    TextButton(onClick = { onSave(holding.copy(shares = holding.shares + 100, costPrice = holding.costPrice)) }) { Text("+100") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(holding.copy(shares = shares, costPrice = cost)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PortfolioAnalysisDialog(vm: SharedViewModel, onDismiss: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 组合分析（${vm.accounts.firstOrNull { it.id == vm.currentHoldingAccountId }?.name ?: ""}）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (vm.portfolioLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("分析中，请稍候…")
                    }
                } else {
                    vm.portfolioError?.let {
                        Text(it, color = DsaRed, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.runPortfolioAnalysisAndSave() }, enabled = !vm.portfolioLoading) { Text("重新分析") }
                    }
                    vm.portfolioReport?.let {
                        Text(it, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        if (!saved) {
                            Text("✓ 报告已自动保存到历史", color = DsaGreen, fontSize = 12.sp)
                            saved = true
                        }
                    }
                    if (vm.portfolioReport == null && vm.portfolioError == null && !vm.portfolioLoading) {
                        Button(onClick = { vm.runPortfolioAnalysisAndSave() }) { Text("开始分析") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun HistoryDialog(vm: SharedViewModel, onDismiss: () -> Unit) {
    val reports = vm.portfolioReports
    val changes = vm.holdingChangeReports
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("历史报告") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("组合分析报告（${reports.size} 份）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                if (reports.isEmpty()) {
                    Text("暂无组合分析历史", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                reports.takeLast(10).reversed().forEach { r ->
                    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(r.createdAt)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.portfolioReport = r.content },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${r.accountName} · ${r.stockCount}只 · %04d-%02d-%02d".format(dt.year, dt.monthNumber, dt.dayOfMonth), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(r.content.take(80) + if (r.content.length > 80) "…" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("持仓变化分析（${changes.size} 份）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                if (changes.isEmpty()) {
                    Text("暂无变化分析历史", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                changes.takeLast(10).reversed().forEach { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.portfolioReport = c.content },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${c.accountName} 变化分析 · %04d-%02d-%02d".format(
                                kotlinx.datetime.Instant.fromEpochMilliseconds(c.createdAt).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).year,
                                kotlinx.datetime.Instant.fromEpochMilliseconds(c.createdAt).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).monthNumber,
                                kotlinx.datetime.Instant.fromEpochMilliseconds(c.createdAt).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).dayOfMonth,
                            ), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(c.content.take(80) + if (c.content.length > 80) "…" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun HoldingChangeDialog(vm: SharedViewModel, onDismiss: () -> Unit) {
    val snapshots = vm.holdingSnapshots.filter { it.accountId == vm.currentHoldingAccountId }
    var oldId by remember { mutableStateOf(snapshots.getOrNull(snapshots.size - 2)?.id ?: 0L) }
    var newId by remember { mutableStateOf(snapshots.lastOrNull()?.id ?: 0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("持仓变化跟踪") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("当前账号快照：${snapshots.size} 份", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                if (snapshots.size < 2) {
                    Text("需要至少 2 份快照才能对比。每次添加/修改持仓会自动保存快照。", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.saveSnapshotNow() }) { Text("保存当前快照") }
                } else {
                    snapshots.takeLast(6).reversed().forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                newId = s.id
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "%04d-%02d-%02d  %s  ${s.holdings.size}只".format(
                                    kotlinx.datetime.Instant.fromEpochMilliseconds(s.createdAt).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).year,
                                    kotlinx.datetime.Instant.fromEpochMilliseconds(s.createdAt).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).monthNumber,
                                    kotlinx.datetime.Instant.fromEpochMilliseconds(s.createdAt).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).dayOfMonth,
                                    s.source,
                                ),
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            if (s.id == newId) Text("新快照 ✓", color = DsaGreen, fontSize = 11.sp)
                            else if (s.id == oldId) Text("旧快照", color = DsaRed, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val old = snapshots.firstOrNull { it.id == oldId }
                    val new = snapshots.firstOrNull { it.id == newId }
                    if (old != null && new != null && old.id != new.id) {
                        Button(onClick = { vm.analyzeHoldingChange(old, new) }, enabled = !vm.portfolioLoading) {
                            Text("AI 对比分析（逐股+操作建议）")
                        }
                        if (vm.portfolioLoading) Text("分析中…", fontSize = 12.sp)
                        vm.portfolioError?.let { Text(it, color = DsaRed, fontSize = 12.sp) }
                        vm.portfolioReport?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, fontSize = 12.sp)
                        }
                    } else {
                        Text("请选择两个不同的快照", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
