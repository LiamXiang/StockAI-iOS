package com.dsa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.analysis.Indicators
import com.dsa.app.data.AiApi
import com.dsa.app.data.ChatMessage
import com.dsa.app.data.MarketApi
import com.dsa.app.data.Quote
import com.dsa.app.ui.theme.DsaBlue
import com.dsa.app.ui.theme.DsaGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(vm: SharedViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showStockMenu by remember { mutableStateOf(false) }
    var selectedCode by remember { mutableStateOf<String?>(null) }
    var contextText by remember { mutableStateOf<String?>(null) }
    var contextLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 选中股票后异步构建行情+指标上下文
    LaunchedEffect(selectedCode) {
        val code = selectedCode ?: run {
            contextText = null
            return@LaunchedEffect
        }
        contextLoading = true
        contextText = null
        try {
            val result = withContext(Dispatchers.Default) {
                val q = MarketApi.fetchQuote(code)
                val kl = MarketApi.fetchKline(code, "day", 120)
                val ind = Indicators.computeAll(kl)
                val summary = if (kl.size >= 10) Indicators.summarize(kl, ind) else "数据不足"
                "$summary"
            }
            val q = vm.quotes[vm.normalizeCode(code)]
            val head = q?.let { "${it.name}（${it.code}） 现价${it.price} 涨跌${it.changePct}%" } ?: code
            contextText = "$head\n$result"
        } catch (e: Exception) {
            contextText = null
        } finally {
            contextLoading = false
        }
    }

    fun send(question: String) {
        if (question.isBlank() || loading) return
        val userMsg = ChatMessage("user", question)
        messages = messages + userMsg
        input = ""
        loading = true
        error = null
        scope.launch {
            try {
                val reply = AiApi.chat(
                    vm.currentApiKeys, vm.model,
                    AiApi.buildChatMessages(messages, contextText, question),
                    vm.aiProvider, vm.customBaseUrl,
                )
                messages = messages + ChatMessage("assistant", reply)
            } catch (e: Exception) {
                error = e.message
                messages = messages + ChatMessage("assistant", "⚠️ ${e.message}")
            } finally {
                loading = false
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("问股", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { messages = emptyList() }) { Text("清空") }
        }

        // ===== 选择股票 =====
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(
                    onClick = { showStockMenu = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        selectedCode?.let { vm.quotes[vm.normalizeCode(it)]?.name ?: it } ?: "选择股票 ▾",
                        fontSize = 13.sp,
                    )
                }
                DropdownMenu(expanded = showStockMenu, onDismissRequest = { showStockMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("无（只聊大盘/概念）", fontSize = 13.sp) },
                        onClick = { selectedCode = null; showStockMenu = false },
                    )
                    vm.watchlist.forEach { code ->
                        val q = vm.quotes[vm.normalizeCode(code)]
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${q?.name ?: code}（${code}）",
                                    fontSize = 13.sp,
                                )
                            },
                            onClick = {
                                selectedCode = vm.normalizeCode(code)
                                showStockMenu = false
                            },
                        )
                    }
                    if (vm.watchlist.isEmpty()) {
                        DropdownMenuItem(text = { Text("（暂无自选股，去自选页添加）", fontSize = 12.sp) }, onClick = { showStockMenu = false })
                    }
                }
            }
            if (selectedCode != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { selectedCode = null }) { Text("清除", fontSize = 12.sp) }
            }
        }

        // ===== 上下文摘要卡片 =====
        if (selectedCode != null) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("当前分析上下文", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (contextLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("正在构建行情与指标摘要…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(contextText ?: "（行情摘要生成失败，将只基于问题回答）", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState,
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "我是你的股票分析助手。可以问我：\n· 某只股票现在能不能买\n· 技术指标怎么看\n· 大盘/板块走势\n· 持仓组合建议\n\n点击上方「选择股票」指定关注股票，问答会自动带上该股的行情与技术指标上下文，回答更精准。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
            items(messages) { m ->
                val isUser = m.role == "user"
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    Surface(
                        color = if (isUser) DsaBlue else MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            m.content,
                            fontSize = 14.sp,
                            color = if (isUser) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
            if (loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("思考中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        error?.let {
            Text(it, color = androidx.compose.ui.graphics.Color(0xFFE53E3E), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("输入问题…") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send(input.trim()) }, enabled = input.isNotBlank() && !loading) {
                Icon(Icons.Filled.Send, "发送")
            }
        }
    }
}
