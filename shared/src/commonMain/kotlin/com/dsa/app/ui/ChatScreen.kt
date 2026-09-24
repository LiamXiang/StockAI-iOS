package com.dsa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.data.AiApi
import com.dsa.app.data.ChatMessage
import com.dsa.app.ui.theme.DsaBlue
import com.dsa.app.ui.theme.DsaGreen
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: SharedViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 当前关注股票上下文
    val context = vm.watchlist.firstOrNull()?.let { code ->
        val q = vm.quotes[vm.normalizeCode(code)]
        if (q != null) "${q.name}（${q.code}）现价${q.price} 涨跌${q.changePct}%" else null
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
                    AiApi.buildChatMessages(messages, context, question),
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
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("问股", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { messages = emptyList() }) { Text("清空") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "我是你的股票分析助手。可以问我：\n· 某只股票现在能不能买\n· 技术指标怎么看\n· 大盘/板块走势\n· 持仓组合建议",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 30.dp),
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
