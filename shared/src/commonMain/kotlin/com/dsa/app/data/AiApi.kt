package com.dsa.app.data

import com.dsa.app.util.Fmt

import com.dsa.app.analysis.Indicators
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 多服务商 AI 层（OpenAI 兼容接口，KMP 版 Ktor 客户端）
 */
object AiApi {

    /** 支持的服务商列表 */
    val PROVIDERS = listOf(
        AiProvider("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1"),
        AiProvider("openai", "OpenAI", "https://api.openai.com/v1"),
        AiProvider("zhipu", "智谱AI (GLM)", "https://open.bigmodel.cn/api/paas/v4"),
        AiProvider("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
        AiProvider("moonshot", "月之暗面 (Kimi)", "https://api.moonshot.cn/v1"),
        AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1"),
        AiProvider("groq", "Groq", "https://api.groq.com/openai/v1"),
        AiProvider("together", "Together AI", "https://api.together.xyz/v1"),
        AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
        AiProvider("doubao", "字节豆包", "https://ark.cn-beijing.volces.com/api/v3"),
        AiProvider("custom", "自定义", ""),
        // ===== 内置密钥服务商（密钥已加密内置，可直接选用）=====
        AiProvider("mimo", "小米 MiMo", "https://token-plan-cn.xiaomimimo.com/v1"),
        AiProvider("stepfun", "阶跃星辰 StepFun", "https://api.stepfun.com/step_plan/v1"),
        AiProvider("sensenova", "商汤日日新 SenseNova", "https://token.sensenova.cn/v1"),
        AiProvider("agnes", "Agnes AI", "https://apihub.agnes-ai.com/v1"),
        AiProvider("volcano", "火山方舟 GLM", "https://ark.cn-beijing.volces.com/api/coding/v3"),
        AiProvider("minimax", "MiniMax", "https://api.minimaxi.com/v1"),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy { createHttpClient() }

    class AiException(message: String) : Exception(message)

    fun getProvider(id: String): AiProvider = PROVIDERS.find { it.id == id } ?: PROVIDERS.first()

    fun getChatUrl(providerId: String, customBaseUrl: String = ""): String {
        val provider = getProvider(providerId)
        val base = if (provider.id == "custom") customBaseUrl else provider.baseUrl
        return "$base/chat/completions"
    }

    fun getModelsUrl(providerId: String, customBaseUrl: String = ""): String {
        val provider = getProvider(providerId)
        val base = if (provider.id == "custom") customBaseUrl else provider.baseUrl
        return "$base/models"
    }

    /** 调用大模型，多个 Key 依次尝试 */
    suspend fun chat(apiKeys: List<String>, model: String, messages: List<ChatMessage>, providerId: String = "siliconflow", customBaseUrl: String = ""): String {
        if (apiKeys.isEmpty()) throw AiException("请先在设置中填写 API Key")
        var lastError: Exception? = null
        for (key in apiKeys) {
            if (key.isBlank()) continue
            try {
                return callOnce(key, model, messages, providerId, customBaseUrl)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: AiException("AI 请求失败")
    }

    private suspend fun callOnce(apiKey: String, model: String, messages: List<ChatMessage>, providerId: String, customBaseUrl: String): String =
        withContext(Dispatchers.Default) {
            val payload = if (providerId == "custom") {
                val msgsJson = messages.joinToString(",") { m ->
                    """{"role":"${m.role}","content":${json.encodeToString(m.content)}}"""
                }
                """{"model":${json.encodeToString(model)},"messages":[$msgsJson]}"""
            } else {
                json.encodeToString(ChatRequest(model = model, messages = messages))
            }
            val url = getChatUrl(providerId, customBaseUrl)
            var lastException: Exception? = null
            for (attempt in 1..2) {
                try {
                    val resp = client.post(url) {
                        header("Authorization", "Bearer $apiKey")
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }
                    val body = resp.bodyAsText()
                    if (resp.status.value !in 200..299) {
                        val parsedMsg = try {
                            json.decodeFromString<ChatResponse>(body).error?.message
                        } catch (e: Exception) { null }
                        val detail = parsedMsg ?: body.take(500)
                        throw AiException("AI 接口错误（${resp.status.value}）: $detail\n请求URL: $url")
                    }
                    val parsed = try {
                        json.decodeFromString<ChatResponse>(body)
                    } catch (e: Exception) {
                        throw AiException("AI 响应解析失败: ${body.take(200)}")
                    }
                    val content = parsed.choices?.firstOrNull()?.message?.content?.trim()
                        ?: throw AiException("AI 返回内容为空")
                    return@withContext content
                } catch (e: AiException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < 2) delay(1000)
                }
            }
            throw lastException ?: AiException("AI 请求失败")
        }

    /** 拉取模型列表 */
    suspend fun fetchModels(apiKey: String, providerId: String, customBaseUrl: String = ""): List<String> =
        withContext(Dispatchers.Default) {
            if (apiKey.isBlank()) throw AiException("请先填写 API Key")
            val url = getModelsUrl(providerId, customBaseUrl)
            val resp = client.get(url) {
                header("Authorization", "Bearer $apiKey")
                header("Content-Type", "application/json")
            }
            val body = resp.bodyAsText()
            if (resp.status.value !in 200..299) {
                throw AiException("拉取模型失败 HTTP ${resp.status.value}: ${body.take(200)}")
            }
            val parsed = try { json.decodeFromString<ModelsResponse>(body) } catch (e: Exception) {
                throw AiException("模型列表解析失败")
            }
            parsed.data?.map { it.id }?.sorted() ?: emptyList()
        }

    /** 测试 API 连接 */
    suspend fun testConnection(apiKey: String, model: String, providerId: String, customBaseUrl: String = ""): String {
        val messages = listOf(ChatMessage("user", "hi"))
        return chat(listOf(apiKey), model, messages, providerId, customBaseUrl)
    }

    /** 构造分析报告请求 */
    fun buildAnalysisMessages(
        quote: Quote?,
        kline: List<KlinePoint>,
        ind: IndicatorResult,
        userRequest: String = "",
    ): List<ChatMessage> {
        val sb = StringBuilder()
        sb.append("请以资深A股技术分析师身份，对以下股票做一份中文分析报告。\n\n")
        if (quote != null) {
            sb.append("【实时行情】\n")
            sb.append("名称: ${quote.name}（${quote.code}）\n")
            sb.append("现价: " + Fmt.d(quote.price) + "，涨跌: " + Fmt.s(quote.change) + "（" + Fmt.s(quote.changePct) + "%），今开: " + Fmt.d(quote.open) + "，最高: " + Fmt.d(quote.high) + "，最低: " + Fmt.d(quote.low) + "\n")
            sb.append("成交量: ${quote.volume}手，成交额: " + Fmt.d(quote.amount) + "万元，换手率: " + Fmt.d(quote.turnover) + "%\n")
            if (quote.pe > 0) sb.append("市盈率(PE): " + Fmt.d(quote.pe) + "，市净率(PB): " + Fmt.d(quote.pb) + "\n")
            if (quote.marketCap > 0) sb.append("总市值: " + Fmt.d(quote.marketCap) + "亿，流通市值: " + Fmt.d(quote.floatCap) + "亿\n")
            sb.append("\n")
        }
        if (kline.isNotEmpty()) {
            sb.append("【技术面摘要】\n")
            sb.append(Indicators.summarize(kline, ind))
            sb.append("均线状态: ${Indicators.maTrend(ind, kline.size - 1)}\n")
            sb.append("\n")
            // 确定性技术信号（本地规则引擎，借鉴归类专家 matchByRule 思路）
            val signals = com.dsa.app.analysis.SignalRules.analyze(ind, quote, kline)
            if (signals.isNotEmpty()) {
                sb.append("【确定性技术信号（本地规则引擎）】\n")
                sb.append(com.dsa.app.analysis.SignalRules.summarize(signals))
                sb.append("\n")
            }
            // 本地知识库增强（词频向量检索，借鉴归类专家 loadData+cosine 思路）
            val kbQuery = (quote?.name ?: "") + " 技术分析 买卖 风险 仓位"
            val kb = KnowledgeBase.retrieve(kbQuery, 3)
            if (kb.isNotBlank()) {
                sb.append(kb).append("\n")
            }
            sb.append("【近12日K线】\n")
            kline.takeLast(12).forEach { k ->
                sb.append(k.day + " 开" + Fmt.d(k.open) + " 高" + Fmt.d(k.high) + " 低" + Fmt.d(k.low) + " 收" + Fmt.d(k.close) + " 量" + Fmt.d(k.volume, 0) + "\n")
            }
        }
        if (userRequest.isNotBlank()) {
            sb.append("\n【用户额外关注】\n").append(userRequest).append("\n")
        }
        sb.append("""
            |
            |请严格按照以下 Markdown 结构输出：
            |## 核心结论
            |（一句话定调，明确看多/看空/观望 + 理由）
            |## 技术评分
            |0-100 分，附一句话解释
            |## 趋势研判
            |短期/中期趋势判断，支撑位、压力位
            |## 买卖建议
            |明确给出：现价能否买、回踩哪里关注、突破哪里加仓、跌破哪里止损
            |## 风险提示
            |列出 2-3 条具体风险
            |## 操作检查清单
            |3-5 条可执行条目（含仓位建议）
            |
            |【重要规则】
            |1. 所有数字必须严格使用上面提供的行情数据，不得编造、推测或修改任何数字
            |2. 不要输出任何关于数据验证、自我怀疑、元注释、英文思考过程的内容
            |3. 如果某项数据不足，直接说明"数据不足"，不要猜测或编造
            |4. 直接给出分析结论，不要在报告末尾附加任何备注、说明或免责声明
            |5. 不保证收益，提示风险
        """.trimMargin())
        return listOf(
            ChatMessage("system", "你是资深A股技术分析师，擅长结合技术指标给出稳健、可执行的中文分析。要求：观点明确、数据准确、风险意识强。禁止输出任何自我怀疑、数据验证、元注释或英文思考内容，直接给出专业分析结论。"),
            ChatMessage("user", sb.toString()),
        )
    }

    /** 问股对话消息 */
    fun buildChatMessages(history: List<ChatMessage>, context: String?, question: String): List<ChatMessage> {
        val msgs = mutableListOf<ChatMessage>()
        msgs.add(ChatMessage("system",
            "你是股票分析助手。当前关注股票上下文：\n${context ?: "无"}\n" +
                "回答要简洁、专业、数据准确，涉及买卖建议必须提示风险。"))
        msgs.addAll(history)
        msgs.add(ChatMessage("user", question))
        return msgs
    }

    /** 组合分析 */
    fun buildPortfolioAnalysis(
        items: List<PortfolioItem>,
        portfolioType: String,
    ): List<ChatMessage> {
        val sb = StringBuilder()
        sb.append("请以资深A股投资顾问身份，对以下$portfolioType 做一份整体组合分析报告。\n\n")
        sb.append("【组合概览】共 ${items.size} 只股票\n\n")
        items.forEachIndexed { idx, item ->
            sb.append("=== ${idx + 1}. ${item.name}（${item.code}）===\n")
            if (item.shares > 0) {
                sb.append("持仓: ${item.shares}股，成本价: " + Fmt.d(item.costPrice) + "，现价: " + Fmt.d(item.price) + "，浮盈: " + Fmt.s(if (item.costPrice > 0) (item.price - item.costPrice) / item.costPrice * 100 else 0.0) + "%\n")
            }
            sb.append("现价: " + Fmt.d(item.price) + "，涨跌: " + Fmt.s(item.changePct) + "%\n")
            if (item.summary.isNotBlank()) sb.append(item.summary).append("\n")
            if (item.signalText.isNotBlank()) sb.append(item.signalText).append("\n")
            sb.append("\n")
        }
        // 本地知识库增强（组合层面检索仓位/风险/买卖决策规则）
        val kb = KnowledgeBase.retrieve("组合 仓位 风险 止损 减仓 加仓", 3)
        if (kb.isNotBlank()) {
            sb.append(kb).append("\n")
        }
        sb.append("""
            |
            |请严格按照以下 Markdown 结构输出整体报告：
            |## 组合总评
            |（一句话定调组合整体强弱，平均涨跌、行业分布、风险等级）
            |## 个股点评
            |（每只股票 1-2 句：当前状态、关键位、操作建议）
            |## 仓位与配置建议
            |（哪些该加仓/减仓/清仓，仓位比例建议）
            |## 重点关注
            |（2-3 只最值得关注的股票及理由）
            |## 风险提示
            |（组合层面的 2-3 条风险）
            |
            |【重要规则】
            |1. 所有数字必须严格使用上面提供的行情数据，不得编造、推测或修改任何数字
            |2. 浮盈比例计算公式：(现价-成本价)/成本价×100%，请严格按此计算
            |3. 不要输出任何关于数据验证、自我怀疑、元注释、英文思考过程的内容
            |4. 如果某项数据不足，直接说明"数据不足"，不要猜测或编造
            |5. 直接给出分析结论，不要在报告末尾附加任何备注、说明或免责声明
            |6. 不保证收益，提示风险
            |7. 【完整性要求】个股点评必须逐一覆盖上列的每一只股票（共 ${items.size} 只），每只都要给出明确操作建议（加仓/持有/减仓/清仓+理由），遗漏任何一只即为不合格
        """.trimMargin())
        return listOf(
            ChatMessage("system", "你是资深A股投资顾问，擅长组合分析与仓位管理。要求：观点明确、数据准确、风险意识强。禁止输出任何自我怀疑、数据验证、元注释或英文思考内容，直接给出专业分析结论。"),
            ChatMessage("user", sb.toString()),
        )
    }

    /** 组合分析单只股票摘要 */
    data class PortfolioItem(
        val code: String,
        val name: String,
        val price: Double,
        val changePct: Double,
        val summary: String,
        val shares: Int = 0,
        val costPrice: Double = 0.0,
        val signalText: String = "",
    )

    /** OCR 识别图片文字（服务商/模型可在设置中配置） */
    suspend fun ocrImage(
        apiKeys: List<String>,
        base64Image: String,
        providerId: String = "siliconflow",
        model: String = "PaddlePaddle/PaddleOCR-VL-1.5",
        customBaseUrl: String = "",
    ): String =
        withContext(Dispatchers.Default) {
            if (apiKeys.isEmpty()) throw AiException("请先在设置中填写 API Key")
            var lastError: Exception? = null
            for (key in apiKeys) {
                if (key.isBlank()) continue
                try {
                    return@withContext ocrOnce(key, base64Image, providerId, model, customBaseUrl)
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: AiException("OCR 识别失败")
        }

    private suspend fun ocrOnce(
        apiKey: String,
        base64Image: String,
        providerId: String,
        model: String,
        customBaseUrl: String,
    ): String {
        val payload = """
            {
              "model": "$model",
              "messages": [
                {"role": "user", "content": [
                  {"type": "text", "text": "请识别图片中所有文字，按行输出。特别注意6位数字的股票代码和股票名称。"},
                  {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,$base64Image"}}
                ]}
              ],
              "max_tokens": 4096
            }
        """.trimIndent()
        val resp = client.post(getChatUrl(providerId, customBaseUrl)) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = resp.bodyAsText()
        if (resp.status.value !in 200..299) throw AiException("OCR HTTP ${resp.status.value}: $body")
        val parsed = try { json.decodeFromString<ChatResponse>(body) } catch (e: Exception) {
            throw AiException("OCR 响应解析失败")
        }
        return parsed.choices?.firstOrNull()?.message?.content?.trim()
            ?: throw AiException("OCR 返回为空")
    }

    /** 持仓截图 OCR：两步法（第一步识别文字，第二步结构化提取持仓） */
    suspend fun ocrHoldingImage(
        apiKeys: List<String>,
        base64Image: String,
        providerId: String = "siliconflow",
        model: String = "PaddlePaddle/PaddleOCR-VL-1.5",
        extractModel: String = "THUDM/GLM-4-9B-0414",
        customBaseUrl: String = "",
    ): String {
        if (apiKeys.isEmpty()) throw AiException("请先在设置中填写 API Key")
        val ocrText = ocrImage(apiKeys, base64Image, providerId, model, customBaseUrl)
        val prompt = """
            以下是股票持仓截图的OCR文字识别结果。请从中提取每只持仓股票的信息，只输出JSON数组，格式：[{"name":"股票名称","shares":持仓数量整数,"costPrice":成本价数字}]
            规则：
            - 股票名称通常是2-4个中文字
            - 持仓数量是"持仓/可用"列中的整数
            - 成本价是"成本/现价"列中的第一个数字，第二个是现价，忽略现价
            - 只识别持仓股列表中的股票，忽略顶部账户汇总信息（总资产、市值等）
            - 如果某只股票的数量或成本价看不清，填0
            - 输出必须是合法JSON，不要用markdown代码块包裹，不要输出其他文字
            OCR文字：
            $ocrText
        """.trimIndent()
        val messages = listOf(ChatMessage(role = "user", content = prompt))
        return chat(apiKeys, extractModel, messages, providerId, customBaseUrl)
    }
}
