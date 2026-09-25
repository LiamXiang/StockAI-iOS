package com.dsa.app.ui

import com.dsa.app.util.Fmt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.analysis.StockScreener
import com.dsa.app.data.*
import com.dsa.app.ui.theme.DsaGreen
import com.dsa.app.ui.theme.DsaRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private fun fmt2(v: Double): String = if (v == 0.0) "—" else Fmt.d(v)

private enum class WatchTab { WATCH, HOLDING }
private enum class SortBy(val label: String) { DEFAULT("默认"), CHANGE_DESC("涨幅↓"), CHANGE_ASC("涨幅↑"), CODE("代码") }

/** OCR 返回的持仓项（JSON 解析用） */
@Serializable
private data class OcrHoldingItem(
    val name: String = "",
    val shares: Int = 0,
    val costPrice: Double = 0.0,
)

private data class DetectedStock(
    val code: String,
    val name: String,
    val price: Double,
    val changePct: Double,
    val shares: Int = 0,
    val costPrice: Double = 0.0,
)

private val ocrJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Composable
fun WatchlistScreen(
    vm: SharedViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(WatchTab.WATCH) }
    var sortBy by remember { mutableStateOf(SortBy.DEFAULT) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHolding by remember { mutableStateOf<String?>(null) }
    var showAnalysis by remember { mutableStateOf(false) }
    var showScreener by remember { mutableStateOf(false) }
    var showOcr by remember { mutableStateOf(false) }
    var showHoldingChange by remember { mutableStateOf(false) }
    var showPortfolioHistory by remember { mutableStateOf(false) }
    var showAccountManage by remember { mutableStateOf(false) }

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
            // ===== 顶部栏 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (tab == WatchTab.WATCH) "自选股" else "持仓股",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // 批量分析
                IconButton(onClick = { showAnalysis = true }) {
                    Icon(Icons.Filled.AutoAwesome, "批量分析", tint = MaterialTheme.colorScheme.onPrimary)
                }
                // 智能选股
                IconButton(onClick = { showScreener = true }) {
                    Icon(Icons.Filled.Search, "智能选股", tint = MaterialTheme.colorScheme.onPrimary)
                }
                // 截图识别
                IconButton(onClick = { showOcr = true }) {
                    Icon(Icons.Filled.Image, "截图识别", tint = MaterialTheme.colorScheme.onPrimary)
                }
                // 持仓变化（仅持仓页）
                if (tab == WatchTab.HOLDING) {
                    IconButton(onClick = { showHoldingChange = true }) {
                        Icon(Icons.Filled.SwapHoriz, "持仓变化", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                // 历史报告（仅持仓页）
                if (tab == WatchTab.HOLDING) {
                    IconButton(onClick = { showPortfolioHistory = true }) {
                        Icon(Icons.Filled.History, "历史报告", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                if (vm.loadingQuotes) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { vm.refreshQuotes() }) {
                        Icon(Icons.Filled.Refresh, "刷新", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // ===== Tab 切换 =====
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = tab == WatchTab.WATCH,
                    onClick = { tab = WatchTab.WATCH },
                    label = { Text("自选股 (${vm.watchlist.size})", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = tab == WatchTab.HOLDING,
                    onClick = { tab = WatchTab.HOLDING },
                    label = { Text("持仓股 (${vm.holdings.size})", fontSize = 12.sp) },
                )
            }

            // ===== 账号切换（仅持仓页）=====
            if (tab == WatchTab.HOLDING) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    vm.accounts.forEach { acc ->
                        FilterChip(
                            selected = vm.currentHoldingAccountId == acc.id,
                            onClick = { vm.switchHoldingAccount(acc.id) },
                            label = { Text(acc.name, fontSize = 12.sp) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            val name = "账号${vm.accounts.size + 1}"
                            vm.addHoldingAccount(name)
                            showToast("已添加 $name")
                        },
                        label = { Text("+ 添加", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showAccountManage = true },
                        label = { Text("管理", fontSize = 12.sp) },
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            // ===== 排序 =====
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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

            // ===== 列表 =====
            if (codes.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(if (tab == WatchTab.WATCH) "暂无自选股" else "暂无持仓股", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (tab == WatchTab.WATCH)
                            "点击右下角 + 添加股票\n支持输入代码（如 600519）或名称/拼音搜索\n也可点顶部图片图标用截图批量识别"
                        else
                            "点击右下角 + 添加持仓股\n支持输入代码或名称搜索，填写数量与成本价\n也可点顶部图片图标用持仓截图批量导入",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
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
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Filled.Add, "添加", tint = MaterialTheme.colorScheme.onPrimary)
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
                onSave = { h ->
                    vm.updateHolding(h)
                    editingHolding = null
                    vm.saveSnapshotNow()
                },
            )
        }
    }

    if (showAnalysis) {
        PortfolioAnalysisDialog(vm = vm, tab = tab, onDismiss = { showAnalysis = false })
    }
    if (showScreener) {
        StockScreenerDialog(vm = vm, tab = tab, onDismiss = { showScreener = false }, onOpenDetail = onOpenDetail)
    }
    if (showOcr) {
        OcrImportDialog(vm = vm, tab = tab, onDismiss = { showOcr = false })
    }
    if (showHoldingChange) {
        HoldingChangeDialog(vm = vm, onDismiss = { showHoldingChange = false })
    }
    if (showPortfolioHistory) {
        PortfolioHistoryDialog(vm = vm, onDismiss = { showPortfolioHistory = false })
    }
    if (showAccountManage) {
        AccountManageDialog(vm = vm, onDismiss = { showAccountManage = false })
    }
}

// ===== 卡片 =====
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
                    q?.let { "今开 ${fmt2(it.open)}  最高 ${fmt2(it.high)}  最低 ${fmt2(it.low)}" } ?: "…",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
                )
            }
            val color = when {
                q == null -> MaterialTheme.colorScheme.outline
                q.isUp -> DsaRed
                else -> DsaGreen
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(q?.let { Fmt.d(it.price) } ?: "—", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
                Spacer(Modifier.height(2.dp))
                Text(q?.let { Fmt.s(it.change) + "  " + Fmt.s(it.changePct) + "%" } ?: "—", fontSize = 12.sp, color = color)
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
                    "持仓 ${holding.shares}股  成本 ${fmt2(holding.costPrice)}  浮盈 ${if (profitPct >= 0) "+" else ""}${Fmt.d(profitPct)}%",
                    fontSize = 12.sp, color = profitColor,
                )
            }
            val color = when {
                q == null -> MaterialTheme.colorScheme.outline
                q.isUp -> DsaRed
                else -> DsaGreen
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(q?.let { Fmt.d(it.price) } ?: "—", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
                Spacer(Modifier.height(2.dp))
                Text(q?.let { Fmt.s(it.change) + "  " + Fmt.s(it.changePct) + "%" } ?: "—", fontSize = 12.sp, color = color)
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

// ===== 持仓汇总卡 =====
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
                Column { Text("总市值", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Fmt.d(totalValue), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Column { Text("总成本", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Fmt.d(totalCost), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("总盈亏", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Fmt.s(profit), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color) }
                Column { Text("盈亏比例", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(Fmt.s(profitPct) + "%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color) }
            }
        }
    }
}

// ===== 添加对话框 =====
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
                    placeholder = { Text("代码 / 名称 / 拼音，如 600519、茅台") },
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
                    Text("无匹配结果，可直接点下方「添加代码」", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
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

// ===== 持仓编辑对话框（含快捷买入/卖出）=====
@Composable
private fun HoldingEditDialog(
    holding: Holding,
    quote: Quote?,
    onDismiss: () -> Unit,
    onSave: (Holding) -> Unit,
) {
    var sharesText by remember { mutableStateOf(holding.shares.let { if (it > 0) it.toString() else "" }) }
    var costText by remember { mutableStateOf(holding.costPrice.let { if (it > 0) Fmt.d(it) else "" }) }
    var tradeShares by remember { mutableStateOf("") }
    var tradePrice by remember { mutableStateOf("") }

    val currentShares = sharesText.toIntOrNull() ?: 0
    val currentCost = costText.toDoubleOrNull() ?: 0.0
    val tShares = tradeShares.toIntOrNull() ?: 0
    val tPrice = tradePrice.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑持仓 - ${quote?.name ?: holding.code}") },
        text = {
            Column {
                Text("当前持仓", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
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
                if (currentShares > 0 && currentCost > 0 && quote != null) {
                    Spacer(Modifier.height(4.dp))
                    val profit = (quote.price - currentCost) * currentShares
                    val profitPct = (quote.price - currentCost) / currentCost * 100
                    val c = if (profit >= 0) DsaRed else DsaGreen
                    Text(
                        "市值 ${Fmt.d(quote.price * currentShares)}  浮盈 ${Fmt.s(profit)} (${Fmt.s(profitPct)}%)",
                        fontSize = 12.sp, color = c,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("快捷买入/卖出", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tradeShares,
                        onValueChange = { tradeShares = it.filter { c -> c.isDigit() } },
                        label = { Text("数量(股)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tradePrice,
                        onValueChange = { tradePrice = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("价格") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (tShares <= 0 || tPrice <= 0) return@Button
                            val newShares = currentShares + tShares
                            val newCost = if (currentShares > 0 && currentCost > 0)
                                (currentShares * currentCost + tShares * tPrice) / newShares
                            else tPrice
                            sharesText = newShares.toString()
                            costText = Fmt.d(newCost)
                            tradeShares = ""; tradePrice = ""
                        },
                        enabled = tShares > 0 && tPrice > 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DsaRed),
                    ) { Text("买入", color = Color.White) }
                    Button(
                        onClick = {
                            if (tShares <= 0 || tShares > currentShares) return@Button
                            val newShares = currentShares - tShares
                            sharesText = if (newShares > 0) newShares.toString() else ""
                            if (newShares <= 0) costText = ""
                            tradeShares = ""; tradePrice = ""
                        },
                        enabled = tShares > 0 && tShares <= currentShares,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DsaGreen),
                    ) { Text("卖出", color = Color.White) }
                }
                if (tShares > currentShares) {
                    Spacer(Modifier.height(4.dp))
                    Text("卖出数量不能超过持仓数量", fontSize = 11.sp, color = DsaRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Holding(code = holding.code, shares = currentShares, costPrice = currentCost))
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ===== 批量组合分析对话框 =====
@Composable
private fun PortfolioAnalysisDialog(
    vm: SharedViewModel,
    tab: WatchTab,
    onDismiss: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var savedFlag by remember { mutableStateOf(false) }
    val accName = vm.accounts.firstOrNull { it.id == vm.currentHoldingAccountId }?.name ?: "账号1"
    val titleText = if (tab == WatchTab.HOLDING) "持仓组合分析（$accName）" else "自选股组合分析"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column {
                if (vm.portfolioLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在分析…", fontSize = 14.sp)
                    }
                }
                vm.portfolioError?.let {
                    Text("⚠️ $it", color = DsaRed, fontSize = 14.sp)
                }
                vm.portfolioReport?.let { r ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(r, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    if (savedFlag) {
                        Spacer(Modifier.height(4.dp))
                        Text("✓ 已保存到历史", color = DsaGreen, fontSize = 12.sp)
                    }
                }
                if (vm.portfolioReport == null && vm.portfolioError == null && !vm.portfolioLoading) {
                    Text("点击「开始分析」生成组合分析报告", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = {
            if (!vm.portfolioLoading && vm.portfolioReport != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        if (tab == WatchTab.HOLDING) {
                            vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = false)
                        } else {
                            vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = true)
                        }
                        savedFlag = true
                    }) { Text("保存") }
                    TextButton(onClick = { showHistory = true }) { Text("历史") }
                    TextButton(onClick = {
                        val r = vm.portfolioReport ?: return@TextButton
                        shareText(titleText, r)
                    }) { Text("导出") }
                    TextButton(onClick = {
                        if (tab == WatchTab.HOLDING) {
                            vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = false)
                        } else {
                            vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = true)
                        }
                        savedFlag = false
                    }) { Text("重新分析") }
                }
            } else if (!vm.portfolioLoading && vm.portfolioError != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showHistory = true }) { Text("历史") }
                    TextButton(onClick = {
                        if (tab == WatchTab.HOLDING) vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = false)
                        else vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = true)
                    }) { Text("重试") }
                }
            } else {
                TextButton(onClick = {
                    if (tab == WatchTab.HOLDING) vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = false)
                    else vm.runPortfolioAnalysisAndSave(vm.currentHoldingAccountId, watch = true)
                    savedFlag = false
                }, enabled = !vm.portfolioLoading) { Text("开始分析") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )

    if (showHistory) {
        PortfolioHistoryDialog(vm = vm, onDismiss = { showHistory = false })
    }
}

// ===== 组合分析历史对话框（按账号过滤，可查看/导出/删除）=====
@Composable
private fun PortfolioHistoryDialog(
    vm: SharedViewModel,
    onDismiss: () -> Unit,
) {
    var viewing by remember { mutableStateOf<PortfolioReport?>(null) }
    val accId = vm.currentHoldingAccountId
    val reports = vm.portfolioReports.filter { it.accountId == accId }
    val accName = vm.accounts.firstOrNull { it.id == accId }?.name ?: "账号1"

    if (viewing != null) {
        val r = viewing!!
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("${r.accountName} - 组合分析报告", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "账号：${r.accountName}  时间：${Fmt.time(r.createdAt)}  模型：${r.model}  股票数：${r.stockCount}",
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
                        shareText("${r.accountName} - 组合分析报告", r.content)
                    }) { Text("导出") }
                    TextButton(onClick = {
                        vm.deletePortfolioReport(r.id)
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
        title = { Text("$accName - 历史组合分析报告（${reports.size}）", fontSize = 16.sp) },
        text = {
            if (reports.isEmpty()) {
                Text("$accName 暂无历史报告", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
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
                                    Text(
                                        "${r.accountName}  ${Fmt.time(r.createdAt)}  ${r.stockCount}只股票",
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "模型：${r.model}  市值：${Fmt.d(r.totalValue, 0)}",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline
                                    )
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

// ===== 截图 OCR 批量识别对话框 =====
@Composable
private fun OcrImportDialog(
    vm: SharedViewModel,
    tab: WatchTab,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ocrText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detected by remember { mutableStateOf<List<DetectedStock>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberImagePicker { bytes ->
        if (bytes == null) {
            if (loading) loading = false
            return@rememberImagePicker
        }
        loading = true; error = null; ocrText = null; detected = emptyList(); selected = emptySet(); status = null
        scope.launch {
            try {
                val base64 = base64Encode(bytes)
                val keys = listOf(vm.apiKey1, vm.apiKey2).filter { it.isNotBlank() }
                // OCR 使用设置页配置的服务商/模型（默认硅基流动 PaddleOCR-VL）
                val ocrProvider = vm.ocrProvider
                val ocrModel = vm.ocrModel
                val ocrExtract = vm.ocrExtractModel
                val ocrBaseUrl = vm.customBaseUrl
                val list = if (tab == WatchTab.HOLDING) {
                    // 持仓模式：识别股票名称、数量、成本价
                    status = "持仓 OCR 识别中（两步：文字识别 + 结构化提取）…"
                    val jsonText = AiApi.ocrHoldingImage(keys, base64, ocrProvider, ocrModel, ocrExtract, ocrBaseUrl)
                    ocrText = jsonText
                    val jsonStart = jsonText.indexOf('[')
                    val jsonEnd = jsonText.lastIndexOf(']')
                    val cleanJson = if (jsonStart >= 0 && jsonEnd > jsonStart) jsonText.substring(jsonStart, jsonEnd + 1) else "[]"
                    val items = try { ocrJson.decodeFromString<List<OcrHoldingItem>>(cleanJson) } catch (e: Exception) { emptyList() }
                    status = "已识别 ${items.size} 只，正在匹配代码与行情…"
                    withContext(Dispatchers.Default) {
                        items.mapNotNull { item ->
                            try {
                                val results = MarketApi.searchStocks(item.name)
                                val match = results.firstOrNull { it.name.contains(item.name) || item.name.contains(it.name) }
                                    ?: results.firstOrNull()
                                if (match != null) {
                                    val code = if (match.type == "zs") "${match.market}${match.code}" else match.code
                                    val q = MarketApi.fetchQuote(code)
                                    if (q != null && q.name.isNotBlank()) {
                                        DetectedStock(code, q.name, q.price, q.changePct, item.shares, item.costPrice)
                                    } else null
                                } else null
                            } catch (e: Exception) { null }
                        }
                    }
                } else {
                    // 自选模式：识别6位数字股票代码
                    status = "OCR 识别中…"
                    val text = AiApi.ocrImage(keys, base64, ocrProvider, ocrModel, ocrBaseUrl)
                    ocrText = text
                    val codes = Regex("""\b(\d{6})\b""").findAll(text)
                        .map { it.groupValues[1] }
                        .filter { it[0] in '0'..'9' }
                        .distinct()
                        .toList()
                    status = "识别到 ${codes.size} 个代码，正在获取行情…"
                    withContext(Dispatchers.Default) {
                        codes.mapNotNull { code ->
                            try {
                                val q = MarketApi.fetchQuote(code)
                                if (q != null && q.name.isNotBlank()) {
                                    DetectedStock(code, q.name, q.price, q.changePct)
                                } else null
                            } catch (e: Exception) { null }
                        }
                    }
                }
                detected = list
                selected = list.map { it.code }.toSet()
                status = null
            } catch (e: Exception) {
                error = e.message ?: "识别失败"
                status = null
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tab == WatchTab.HOLDING) "截图识别持仓股" else "截图批量识别股票") },
        text = {
            Column {
                Text(
                    if (tab == WatchTab.HOLDING)
                        "选择持仓截图（如同花顺/东方财富持仓页），自动识别股票名称、持仓数量、成本价，批量导入当前账号持仓。"
                    else
                        "选择一张包含股票代码/名称的截图（如持仓截图、行情列表），自动识别并批量添加到自选。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(10.dp))
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(status ?: "OCR 识别中…", fontSize = 13.sp)
                    }
                }
                error?.let { Text("⚠️ $it", color = DsaRed, fontSize = 13.sp) }
                if (detected.isNotEmpty()) {
                    Text("识别到 ${detected.size} 只股票，勾选后添加：", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.height(250.dp)) {
                        LazyColumn {
                            items(detected) { s ->
                                val isSel = selected.contains(s.code)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = if (isSel) selected - s.code else selected + s.code
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (isSel) "☑" else "☐", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(s.code, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        if (tab == WatchTab.HOLDING && (s.shares > 0 || s.costPrice > 0)) {
                                            Text(
                                                "持仓 ${s.shares}股  成本 ${Fmt.d(s.costPrice)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                    val c = if (s.changePct >= 0) DsaRed else DsaGreen
                                    Text("${Fmt.d(s.price)}  ${Fmt.s(s.changePct)}%", fontSize = 12.sp, color = c)
                                }
                            }
                        }
                    }
                }
                ocrText?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("OCR 原文（前200字）：", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(it.take(200), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = {
            if (detected.isNotEmpty()) {
                TextButton(
                    onClick = {
                        val count = selected.size
                        if (tab == WatchTab.HOLDING) {
                            val holdings = selected.mapNotNull { code ->
                                val s = detected.find { it.code == code }
                                Holding(code = code, shares = s?.shares ?: 0, costPrice = s?.costPrice ?: 0.0)
                            }
                            vm.addHoldingsAndRefresh(holdings, source = "ocr")
                        } else {
                            vm.addStocksAndRefresh(selected.toList())
                        }
                        showToast("已添加 $count 只${if (tab == WatchTab.HOLDING) "持仓" else "自选"}股")
                        onDismiss()
                    },
                    enabled = selected.isNotEmpty(),
                ) { Text("添加 ${selected.size} 只") }
            } else {
                TextButton(onClick = { pickImage() }) { Text("选择图片") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

// ===== 智能选股对话框 =====
@Composable
private fun StockScreenerDialog(
    vm: SharedViewModel,
    tab: WatchTab,
    onDismiss: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedStrategies by remember { mutableStateOf<Set<String>>(StockScreener.STRATEGIES.map { it.id }.toSet()) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<StockScreener.ScreenResult>>(emptyList()) }
    var hasRun by remember { mutableStateOf(false) }

    val codes = when (tab) {
        WatchTab.WATCH -> vm.watchlist
        WatchTab.HOLDING -> vm.holdings.map { it.code }
    }

    fun runScreening() {
        if (codes.isEmpty() || selectedStrategies.isEmpty()) return
        loading = true
        hasRun = true
        results = emptyList()
        scope.launch {
            try {
                val list = withContext(Dispatchers.Default) {
                    codes.mapNotNull { code ->
                        try {
                            val q = MarketApi.fetchQuote(code)
                            val kl = MarketApi.fetchKline(code, "day", 120)
                            if (kl.size < 10) return@mapNotNull null
                            val ind = com.dsa.app.analysis.Indicators.computeAll(kl)
                            val matched = StockScreener.screenOne(kl, ind).filter { it in selectedStrategies }
                            if (matched.isEmpty()) return@mapNotNull null
                            StockScreener.ScreenResult(
                                code = code,
                                name = q?.name ?: code,
                                price = q?.price ?: 0.0,
                                changePct = q?.changePct ?: 0.0,
                                matchedStrategies = matched,
                                score = StockScreener.calcScore(matched),
                            )
                        } catch (e: Exception) { null }
                    }
                }
                results = list.sortedByDescending { it.score }
            } catch (e: Exception) {
                // 静默失败
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("智能选股", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("在${if (tab == WatchTab.WATCH) "自选股" else "持仓股"}（${codes.size}只）范围内筛选", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text("选股策略（可多选）：", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Column {
                    StockScreener.STRATEGIES.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { s ->
                                val selected = s.id in selectedStrategies
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedStrategies = if (selected) selectedStrategies - s.id else selectedStrategies + s.id
                                    },
                                    label = { Text(s.name, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { runScreening() },
                    enabled = !loading && selectedStrategies.isNotEmpty() && codes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("正在筛选…")
                    } else {
                        Text("开始选股")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (hasRun && !loading && results.isNotEmpty()) {
                    Text("筛选结果（${results.size}只，按评分排序）：", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column {
                            results.forEach { r ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { onOpenDetail(r.code); onDismiss() },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(r.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Spacer(Modifier.width(6.dp))
                                                Text(r.code, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                r.matchedStrategies.joinToString("、") { StockScreener.strategyName(it) },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                Fmt.s(r.changePct) + "%",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (r.changePct >= 0) DsaRed else DsaGreen,
                                            )
                                            Text("评分 ${r.score}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (hasRun && !loading && results.isEmpty()) {
                    Text("没有符合条件的股票", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

// ===== 持仓变化跟踪对话框 =====
@Composable
private fun HoldingChangeDialog(
    vm: SharedViewModel,
    onDismiss: () -> Unit,
) {
    val accId = vm.currentHoldingAccountId
    val snapshots = vm.holdingSnapshots.filter { it.accountId == accId }
    val accName = vm.accounts.firstOrNull { it.id == accId }?.name ?: "账号1"
    var selectedOld by remember { mutableStateOf<Int?>(null) }
    var selectedNew by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(snapshots.size) {
        if (snapshots.size >= 2 && selectedOld == null && selectedNew == null) {
            selectedNew = 0
            selectedOld = 1
        }
    }

    fun computeDiff(oldList: List<Holding>, newList: List<Holding>): String {
        val oldMap = oldList.associateBy { it.code }
        val newMap = newList.associateBy { it.code }
        val allCodes = (oldMap.keys + newMap.keys).sorted()
        val lines = mutableListOf<String>()
        for (code in allCodes) {
            val old = oldMap[code]
            val new = newMap[code]
            when {
                old == null && new != null -> lines.add("➕ 新增：${new.code} ${new.shares}股 成本${new.costPrice}")
                old != null && new == null -> lines.add("➖ 卖出：${old.code} ${old.shares}股 成本${old.costPrice}")
                old != null && new != null -> {
                    if (old.shares != new.shares || old.costPrice != new.costPrice) {
                        val shareDiff = new.shares - old.shares
                        val action = if (shareDiff > 0) "加仓" else if (shareDiff < 0) "减仓" else "调整"
                        lines.add("🔄 $action：${new.code} ${old.shares}→${new.shares}股 成本${old.costPrice}→${new.costPrice}")
                    }
                }
            }
        }
        return if (lines.isEmpty()) "持仓无变化" else lines.joinToString("\n")
    }

    val oldSnapshot = if (selectedOld != null && selectedOld!! < snapshots.size) snapshots[selectedOld!!] else null
    val newSnapshot = if (selectedNew != null && selectedNew!! < snapshots.size) snapshots[selectedNew!!] else null
    val diffText = if (oldSnapshot != null && newSnapshot != null && oldSnapshot.id != newSnapshot.id)
        computeDiff(oldSnapshot.holdings, newSnapshot.holdings) else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$accName - 持仓变化跟踪", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (snapshots.size < 2) {
                    Text(
                        "$accName 持仓快照不足2次，无法对比。\n\n每次通过截图识别导入持仓或编辑持仓后会自动保存快照，目前已有 ${snapshots.size} 次快照。",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.saveSnapshotNow() }) { Text("保存当前快照") }
                } else {
                    Text("选择对比的两次快照：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("较早：", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            snapshots.forEachIndexed { idx, s ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selectedOld = idx }.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = selectedOld == idx, onClick = { selectedOld = idx }, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${Fmt.time(s.createdAt)} (${s.holdings.size}只)", fontSize = 11.sp)
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("较新：", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            snapshots.forEachIndexed { idx, s ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selectedNew = idx }.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = selectedNew == idx, onClick = { selectedNew = idx }, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${Fmt.time(s.createdAt)} (${s.holdings.size}只)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Divider()
                    Spacer(Modifier.height(10.dp))
                    Text("持仓变化：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(if (diffText.isEmpty()) "请选择两个不同的快照" else diffText, fontSize = 12.sp, lineHeight = 18.sp)
                    if (oldSnapshot != null && newSnapshot != null && oldSnapshot.id != newSnapshot.id) {
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AI 变化分析（逐股 + 操作建议）", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { vm.analyzeHoldingChange(oldSnapshot, newSnapshot) },
                                enabled = !vm.portfolioLoading,
                            ) {
                                Text(if (vm.portfolioLoading) "分析中…" else if (vm.portfolioReport != null) "重新分析" else "AI分析", fontSize = 12.sp)
                            }
                        }
                        if (vm.portfolioLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在分析持仓变化…", fontSize = 12.sp)
                            }
                        }
                        vm.portfolioError?.let { Text("⚠️ $it", color = DsaRed, fontSize = 12.sp) }
                        vm.portfolioReport?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("✓ 分析结果已自动保存到历史", color = DsaGreen, fontSize = 11.sp)
                            Text(it, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showHistory = true }) { Text("历史") }
                vm.portfolioReport?.let { analysis ->
                    TextButton(onClick = {
                        shareText("$accName - 持仓变化分析报告", "变化详情：\n$diffText\n\nAI分析：\n$analysis")
                    }) { Text("导出") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )

    if (showHistory) {
        HoldingChangeHistoryDialog(
            vm = vm,
            accountName = accName,
            onDismiss = { showHistory = false },
        )
    }
}

// ===== 历史持仓变化分析报告对话框 =====
@Composable
private fun HoldingChangeHistoryDialog(
    vm: SharedViewModel,
    accountName: String,
    onDismiss: () -> Unit,
) {
    var viewing by remember { mutableStateOf<HoldingChangeReport?>(null) }
    val reports = vm.holdingChangeReports.filter { it.accountId == vm.currentHoldingAccountId }

    if (viewing != null) {
        val r = viewing!!
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text("${r.accountName} - 持仓变化分析", fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "账号：${r.accountName}  时间：${Fmt.time(r.createdAt)}  模型：${r.model}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.outline
                    )
                    if (r.oldSnapshotTime > 0 && r.newSnapshotTime > 0) {
                        Text(
                            "对比：${Fmt.time(r.oldSnapshotTime)} → ${Fmt.time(r.newSnapshotTime)}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.outline
                        )
                    }
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
                        shareText("${r.accountName} - 持仓变化分析报告", r.content)
                    }) { Text("导出") }
                    TextButton(onClick = {
                        vm.deleteHoldingChangeReport(r.id)
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
        title = { Text("$accountName - 历史持仓变化分析（${reports.size}）", fontSize = 16.sp) },
        text = {
            if (reports.isEmpty()) {
                Text("$accountName 暂无历史分析报告", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
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
                                    Text("${r.accountName}  ${Fmt.time(r.createdAt)}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (r.oldSnapshotTime > 0 && r.newSnapshotTime > 0) {
                                        Text(
                                            "对比：${Fmt.time(r.oldSnapshotTime)} → ${Fmt.time(r.newSnapshotTime)}",
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.outline
                                        )
                                    }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

// ===== 账号管理对话框（重命名/删除）=====
@Composable
private fun AccountManageDialog(
    vm: SharedViewModel,
    onDismiss: () -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (editingId != null) {
        val acc = vm.accounts.firstOrNull { it.id == editingId }
        if (acc != null) {
            AlertDialog(
                onDismissRequest = { editingId = null },
                title = { Text("重命名账号") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("账号名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (renameText.isNotBlank()) {
                            vm.renameHoldingAccount(acc.id, renameText.trim())
                            showToast("已重命名")
                        }
                        editingId = null
                    }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { editingId = null }) { Text("取消") } },
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账号管理（${vm.accounts.size} 个）", fontSize = 16.sp) },
        text = {
            Column {
                vm.accounts.forEach { acc ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(acc.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("${acc.holdings.size} 只持仓", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        TextButton(onClick = {
                            renameText = acc.name
                            editingId = acc.id
                        }) { Text("重命名", fontSize = 12.sp) }
                        if (vm.accounts.size > 1) {
                            TextButton(onClick = {
                                vm.deleteHoldingAccount(acc.id)
                                showToast("已删除")
                            }) { Text("删除", fontSize = 12.sp, color = DsaRed) }
                        }
                    }
                    Divider()
                }
                Spacer(Modifier.height(4.dp))
                Text("至少保留一个账号", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
