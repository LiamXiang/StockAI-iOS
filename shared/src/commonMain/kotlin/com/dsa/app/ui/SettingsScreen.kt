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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsa.app.data.AiApi
import com.dsa.app.data.showToast
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

        // ===== OCR 识别配置 =====
        Text("OCR 识别配置（截图导入股票）", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text("OCR 可完全独立配置：单独的服务商、API Key 和识别模型，与主分析互不影响。Key 留空则自动使用该服务商已配置的 Key（再留空用主 Key）。", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(6.dp))
        var ocrProv by remember { mutableStateOf(vm.ocrProvider) }
        var ocrModelText by remember { mutableStateOf(vm.ocrModel) }
        var ocrExtractText by remember { mutableStateOf(vm.ocrExtractModel) }
        var ocrKeyText by remember { mutableStateOf(vm.ocrApiKey) }
        var showOcrKey by remember { mutableStateOf(false) }
        var showOcrProvMenu by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = AiApi.getProvider(ocrProv).name,
            onValueChange = {},
            readOnly = true,
            label = { Text("OCR 服务商") },
            trailingIcon = { Text("▾") },
            modifier = Modifier.fillMaxWidth().clickable { showOcrProvMenu = true },
        )
        DropdownMenu(expanded = showOcrProvMenu, onDismissRequest = { showOcrProvMenu = false }) {
            AiApi.PROVIDERS.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.name}（${p.baseUrl}）", fontSize = 12.sp) },
                    onClick = { ocrProv = p.id; showOcrProvMenu = false },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = ocrModelText, onValueChange = { ocrModelText = it },
            label = { Text("识别模型（默认 PaddlePaddle/PaddleOCR-VL-1.5）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = ocrExtractText, onValueChange = { ocrExtractText = it },
            label = { Text("持仓提取模型（默认 THUDM/GLM-4-9B-0414）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = ocrKeyText, onValueChange = { ocrKeyText = it },
            label = { Text("OCR API Key（独立于主 Key，加密保存）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showOcrKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showOcrKey = !showOcrKey }) {
                    Text(if (showOcrKey) "隐藏" else "显示", fontSize = 11.sp)
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                vm.setOcrConfig(ocrProv, ocrModelText.trim().ifBlank { "PaddlePaddle/PaddleOCR-VL-1.5" }, ocrExtractText.trim().ifBlank { "THUDM/GLM-4-9B-0414" }, ocrKeyText.trim())
                showToast("OCR 配置已保存")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存 OCR 配置") }
        Text("支持视觉识别的服务商/模型示例：硅基流动 PaddlePaddle/PaddleOCR-VL-1.5、OpenAI gpt-4o、智谱 glm-4v 等。", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Text(if (vm.ocrApiKey.isNotBlank()) "✓ 已配置独立 OCR Key（${AiApi.getProvider(ocrProv).name}）" else "未配置独立 OCR Key，将自动使用该服务商/主 Key", fontSize = 11.sp, color = if (vm.ocrApiKey.isNotBlank()) DsaGreen else MaterialTheme.colorScheme.outline)

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
        var thsTesting by remember { mutableStateOf(false) }
        var thsTestResult by remember { mutableStateOf<String?>(null) }
        OutlinedTextField(
            value = thsKey, onValueChange = { thsKey = it },
            label = { Text("同花顺 API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.saveThsApiKey(thsKey.trim())
                    showToast("已保存")
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存 Key") }
            Button(
                onClick = {
                    thsTesting = true
                    thsTestResult = null
                    scope.launch {
                        thsTestResult = try {
                            val q = com.dsa.app.data.ThsApi.fetchQuotes(listOf("600519"), thsKey.trim())
                            if (q.isEmpty()) "✗ 连接失败：无返回数据" else "✓ 连接成功！测试: ${q.first().name} ${q.first().price}"
                        } catch (e: Exception) {
                            "✗ 连接失败: ${e.message}"
                        }
                        thsTesting = false
                    }
                },
                enabled = !thsTesting && thsKey.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(if (thsTesting) "测试中…" else "测试连接") }
        }
        thsTestResult?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("✓")) DsaGreen else DsaRed) }
        Text("同花顺失败会自动降级到腾讯数据源", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        Card(Modifier.fillMaxWidth().padding(top = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(Modifier.padding(10.dp)) {
                Text("数据源说明", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("• 腾讯：默认，免费、快，行情字段较全\n• 同花顺：专业金融数据（行情/K线/资金流/财务），需同花顺 API Key\n• 使用同花顺数据时，搜索、K线、行情均走同花顺接口，失败自动降级", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

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
                selected = vm.theme == "system",
                onClick = { vm.saveTheme("system") },
                label = { Text("跟随系统", fontSize = 12.sp) },
            )
            FilterChip(
                selected = vm.theme == "light",
                onClick = { vm.saveTheme("light") },
                label = { Text("浅色", fontSize = 12.sp) },
            )
            FilterChip(
                selected = vm.theme == "dark",
                onClick = { vm.saveTheme("dark") },
                label = { Text("深色", fontSize = 12.sp) },
            )
        }

        Spacer(Modifier.height(14.dp))

        // ===== 关于 / 免责 =====
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(Modifier.padding(12.dp)) {
                Text("关于", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("股票智能分析助手 v1.0\n基于 Compose Multiplatform 跨平台构建", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("免责声明", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("本应用所有 AI 分析与选股结果仅供参考，不构成任何投资建议。股市有风险，投资需谨慎。数据可能存在延迟或误差，请以交易所官方数据为准。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
