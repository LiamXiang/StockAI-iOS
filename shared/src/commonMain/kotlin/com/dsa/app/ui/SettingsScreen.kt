package com.dsa.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.data.AiApi
import com.dsa.app.ui.theme.DsaGreen
import com.dsa.app.ui.theme.DsaRed
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: SharedViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("设置", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        // ===== AI 服务商 =====
        Text("AI 服务商", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        AiApi.PROVIDERS.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().clickableRow { vm.saveAiProvider(p.id) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = vm.aiProvider == p.id, onClick = { vm.saveAiProvider(p.id) })
                Text(p.name, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                if (vm.aiProvider == p.id) Text("✓", color = DsaGreen, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ===== API Key =====
        Text("API Key（服务商切换后自动对应）", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))

        var key1 by remember { mutableStateOf(vm.apiKey1) }
        var key2 by remember { mutableStateOf(vm.apiKey2) }
        var keyCustom by remember { mutableStateOf(vm.providerKeys[vm.aiProvider] ?: "") }
        var baseUrl by remember { mutableStateOf(vm.customBaseUrl) }

        if (vm.aiProvider == "siliconflow") {
            OutlinedTextField(
                value = key1, onValueChange = { key1 = it },
                label = { Text("硅基流动 Key 1") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = key2, onValueChange = { key2 = it },
                label = { Text("硅基流动 Key 2") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.saveKeys(key1.trim(), key2.trim()); testResult = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存 Keys") }
        } else {
            OutlinedTextField(
                value = keyCustom, onValueChange = { keyCustom = it },
                label = { Text("${vm.aiProvider} API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (vm.aiProvider == "custom") {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("自定义 Base URL（如 https://xxx/v1）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.saveProviderKey(vm.aiProvider, keyCustom.trim()); vm.saveCustomBaseUrl(baseUrl.trim()); testResult = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存 Key") }
        }

        Spacer(Modifier.height(10.dp))

        // ===== 模型 =====
        Text("模型", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        var showModelMenu by remember { mutableStateOf(false) }
        val modelOptions = (vm.fetchedModels + vm.model).distinct()
        Box {
            OutlinedButton(onClick = { showModelMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("当前模型: ${vm.model}", fontSize = 13.sp)
            }
            DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                modelOptions.forEach { m ->
                    DropdownMenuItem(text = { Text(m, fontSize = 13.sp) }, onClick = { vm.saveModel(m); showModelMenu = false })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = { vm.fetchModels() }, enabled = !vm.fetchingModels, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.fetchingModels) "拉取中…" else "拉取模型列表")
        }
        vm.fetchModelsError?.let { Text(it, color = DsaRed, fontSize = 12.sp) }
        if (vm.fetchedModels.isNotEmpty()) {
            Text("共 ${vm.fetchedModels.size} 个模型，点上方选择", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(Modifier.height(10.dp))

        // ===== 测试连接 =====
        Button(
            onClick = {
                testing = true
                testResult = null
                scope.launch {
                    val key = if (vm.aiProvider == "siliconflow") vm.apiKey1 else vm.providerKeys[vm.aiProvider] ?: ""
                    testResult = try {
                        val r = AiApi.testConnection(key, vm.model, vm.aiProvider, vm.customBaseUrl)
                        "✓ 连接成功！返回: ${r.take(60)}"
                    } catch (e: Exception) {
                        "✗ 连接失败: ${e.message}"
                    }
                    testing = false
                }
            },
            enabled = !testing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (testing) "测试中…" else "测试连接") }
        testResult?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("✓")) DsaGreen else DsaRed) }

        Spacer(Modifier.height(14.dp))

        // ===== 数据源 =====
        Text("行情数据源", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = vm.dataSource == "tencent",
                onClick = { vm.saveDataSource("tencent") },
                label = { Text("腾讯（默认）", fontSize = 12.sp) },
            )
            FilterChip(
                selected = vm.dataSource == "ths",
                onClick = { vm.saveDataSource("ths") },
                label = { Text("同花顺", fontSize = 12.sp) },
            )
        }
        Spacer(Modifier.height(6.dp))
        var thsKey by remember { mutableStateOf(vm.thsApiKey) }
        OutlinedTextField(
            value = thsKey, onValueChange = { thsKey = it },
            label = { Text("同花顺 API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Button(onClick = { vm.saveThsApiKey(thsKey.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("保存同花顺 Key") }
        Text("同花顺失败会自动降级到腾讯数据源", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

        Spacer(Modifier.height(14.dp))

        // ===== 定时分析 =====
        Text("定时自动分析", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("每天首次打开自动生成持仓组合报告", fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Switch(checked = vm.autoAnalysisEnabled, onCheckedChange = { vm.setAutoAnalysis(it) })
        }

        Spacer(Modifier.height(14.dp))

        // ===== 主题 =====
        Text("主题", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = vm.theme == "dark",
                onClick = { vm.saveTheme("dark") },
                label = { Text("深色", fontSize = 12.sp) },
            )
            FilterChip(
                selected = vm.theme == "light",
                onClick = { vm.saveTheme("light") },
                label = { Text("浅色", fontSize = 12.sp) },
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
