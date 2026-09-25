package com.dsa.app.data

/**
 * 本地知识库 + 轻量向量检索。
 * 借鉴「归类专家」loadData + cosine + termVecCache 的思路：
 * 内置结构化知识条目，用词频向量余弦相似度做本地检索（无需外部 embedding API），
 * 命中条目注入 AI 提示词，作为分析的知识增强（RAG 模式）。
 */
object KnowledgeBase {

    data class Entry(val title: String, val keywords: List<String>, val content: String)

    private val entries: List<Entry> = listOf(
        Entry(
            "均线系统解读", listOf("均线", "MA", "金叉", "死叉", "多头", "空头"),
            "均线是多空趋势的量化表达：多头排列（短期在上）代表趋势向上，空头排列代表趋势向下。" +
                "MA5 上穿 MA20 为短线金叉信号，下穿为死叉。股价站上 20 日均线且均线向上，中期趋势偏多；" +
                "跌破且均线向下，中期偏空。均线粘合后发散往往是趋势启动信号。",
        ),
        Entry(
            "MACD 用法", listOf("MACD", "DIF", "DEA", "动能"),
            "MACD 反映中期动能：DIF 上穿 DEA 为金叉（转强），下穿为死叉（转弱）；柱体由负转正代表多方动能启动。" +
                "零轴上方金叉力度更强，零轴下方金叉多为反弹。顶背离（价格新高但 MACD 不新高）是重要减仓警示，底背离则是买入信号。",
        ),
        Entry(
            "KDJ 用法", listOf("KDJ", "随机指标", "超买", "超卖"),
            "KDJ 是短线摆动指标：J 值>90 超买（注意回调），<10 超卖（存在反弹）；K 上穿 D 为金叉。" +
                "KDJ 在低位钝化后的金叉可靠性更高，高位死叉杀伤力大。震荡市 KDJ 有效，单边趋势中会钝化失效。",
        ),
        Entry(
            "RSI 用法", listOf("RSI", "相对强弱", "超买", "超卖"),
            "RSI 衡量买卖力量强弱：RSI6>80 超买（警惕回调），<20 超卖（存在修复）；50 为强弱分界。" +
                "RSI 与价格背离（价新高 RSI 不新高）预示动能衰竭。趋势行情中 RSI 会长期处于高位或低位，结合趋势判断。",
        ),
        Entry(
            "布林带 BOLL", listOf("BOLL", "布林", "轨道", "波动"),
            "布林带以 20 日均线为中轨、上下各 2 倍标准差为轨道：价格沿上轨运行代表强势，贴下轨代表弱势；" +
                "开口放大预示波动加剧（趋势启动或风险释放），收口预示变盘。突破上轨注意追高风险，跌破下轨往往是超卖。",
        ),
        Entry(
            "量价关系", listOf("量", "成交量", "放量", "缩量", "资金"),
            "量是价的先行指标：放量上涨代表资金介入（健康），放量下跌代表抛压（危险）；" +
                "缩量回调代表抛压减轻（洗盘），缩量上涨代表承接不足（警惕）。天量见天价、地量见地价是常见规律。",
        ),
        Entry(
            "趋势与 DMI/ADX", listOf("DMI", "ADX", "趋势", "+DI", "-DI"),
            "ADX>25 代表趋势明确，ADX<20 代表震荡无趋势。+DI 在 -DI 上方且 ADX 走强 = 上升趋势确认；" +
                "反之下降趋势。趋势明确时顺势操作，震荡时高抛低吸或观望。",
        ),
        Entry(
            "仓位管理", listOf("仓位", "止损", "止盈", "风险", "配置"),
            "个人投资者仓位管理原则：单只股票仓位建议不超过总资产的 20%-30%；" +
                "买入前设定止损位（建议 -8% 至 -10%），跌破坚决执行；分批建仓（如 1/3、1/3、1/3）摊薄成本；" +
                "盈利仓位分批止盈（+15% 减 1/3，+30% 再减 1/3），不因单票涨跌影响整体心态。",
        ),
        Entry(
            "风险控制要点", listOf("风险", "回撤", "止损", "分散"),
            "风险控制优先于收益：单日回撤超 -5% 或累计回撤 -15% 应降仓位复盘；" +
                "避免满仓单一赛道；远离 ST/*ST、长期停牌、财务造假嫌疑股；" +
                "高换手、高波动、小市值个股波动剧烈，仓位需更小；设置硬性止损不心存侥幸。",
        ),
        Entry(
            "财报速读要点", listOf("财报", "营收", "利润", "现金流", "毛利率"),
            "快速评估财报：营收与净利润是否同步增长（增收不增利是警示）；毛利率趋势（提升=竞争力增强）；" +
                "经营现金流是否为正且匹配利润（现金流差于利润有粉饰嫌疑）；应收账款/存货增速是否快于营收；" +
                "资产负债率与有息负债规模；ROE 与净利率水平（>15% 为优）。",
        ),
        Entry(
            "板块联动与轮动", listOf("板块", "概念", "轮动", "龙头"),
            "个股行情常受板块与市场情绪驱动：观察所属板块当日资金流入与龙头股表现；" +
                "龙头领涨、跟风补涨、滞涨补跌是板块轮动的三个阶段；涨停潮后追高需谨慎，板块退潮时先跌的往往是跟风股。",
        ),
        Entry(
            "买入决策清单", listOf("买入", "决策", "信号", "确认"),
            "买入前清单：①趋势向上（均线多头/站上关键均线）；②量能配合（放量突破或缩量回踩）；" +
                "③基本面无硬伤（业绩/现金流/负债）；④有明确止损位；⑤大盘与板块环境不差。" +
                "至少满足 3 条以上再考虑买入，信号相互矛盾时放弃或降低仓位。",
        ),
        Entry(
            "卖出决策清单", listOf("卖出", "止盈", "止损", "减仓"),
            "卖出信号：①跌破止损位（无条件执行）；②放量滞涨或高位放量长上影；" +
                "③MACD/RSI 顶背离；④跌破关键均线（20日）且均线拐头；⑤基本面恶化（业绩爆雷/利空落地）。" +
                "止盈分批：+15% 减 1/3，+30% 再减 1/3，剩余仓位跟踪均线持有。",
        ),
        Entry(
            "CCI 顺势指标", listOf("CCI", "顺势", "超买", "超卖"),
            "CCI 衡量价格偏离统计均值的程度：>+100 进入强势区（趋势行情中可持有），<-100 超卖区（存在反弹）；" +
                "CCI 从 -100 上穿为买入参考，从 +100 下穿为卖出参考。极端值（>+300 或 <-300）代表短线过热/过冷。",
        ),
        Entry(
            "SAR 抛物线", listOf("SAR", "抛物线", "止损", "反转"),
            "SAR 是趋势跟踪与止损指标：价格在 SAR 上方为多头（持仓参考），跌破 SAR 为反转警示（减仓）；" +
                "SAR 随价格移动，牛市贴价上行、熊市压制价格。适合趋势行情，震荡市中信号频繁失效。",
        ),
        Entry(
            "BIAS 乖离率", listOf("BIAS", "乖离", "偏离"),
            "乖离率衡量价格偏离均线的幅度：BIAS6>10% 短线偏离过大（回踩概率增加），<-10% 超跌（修复概率增加）；" +
                "乖离过大结合放量滞涨/K线长上影是短线减仓信号。",
        ),
        Entry(
            "OBV 能量潮", listOf("OBV", "能量潮", "资金"),
            "OBV 以量能累积刻画资金流向：OBV 与价格同向上升 = 资金持续流入；" +
                "价格新高而 OBV 未新高 = 上涨动能不足（背离警示）；OBV 创新高往往领先价格。",
        ),
        Entry(
            "威廉指标 WR", listOf("WR", "威廉", "超买", "超卖"),
            "WR 与 KDJ/RSI 类似为摆动指标：WR<20 超买（注意回调），>80 超卖（存在反弹）；" +
                "WR 与 KDJ 同向共振时信号更可靠；多个摆动指标同时超买/超卖时，反向操作胜率提升。",
        ),
        Entry(
            "ROC 变动率", listOf("ROC", "变动率", "动量"),
            "ROC 衡量 N 期价格变动率：ROC 由负转正 = 动量拐头向上（买入参考）；由正转负 = 动量转弱（卖出参考）；" +
                "ROC 创新高预示趋势加速，与价格背离时警惕反转。",
        ),
        Entry(
            "大盘与市场情绪", listOf("大盘", "指数", "情绪", "环境"),
            "个股分析必须结合大盘环境：指数站上关键均线且量能温和放大 = 做多环境；" +
                "指数破位或缩量阴跌 = 降低仓位防御。市场情绪冰点（普跌、涨停稀少）往往是阶段低点附近，情绪高潮（普涨、涨停潮）要防退潮。",
        ),
        Entry(
            "止损纪律", listOf("止损", "纪律", "执行"),
            "止损是保命线：买入前必须预设止损位（技术位或固定 -8%），触发无条件执行，不允许'扛单'与摊平补仓摊薄亏损。" +
                "止损后重新评估是否满足买入清单，不满足不急于回补。严格执行止损的投资者长期存活率远高于不设止损者。",
        ),
    )

    // ===== 词频向量 + 余弦检索（借鉴归类专家 cosine/termVecCache）=====
    private val termVecCache = mutableMapOf<String, Map<String, Int>>()

    private fun tokenize(text: String): List<String> {
        val normalized = text.lowercase()
        // 中文按字符 + 英文按词
        val tokens = mutableListOf<String>()
        val en = Regex("[a-z0-9]+").findAll(normalized).map { it.value }
        tokens += en
        val zh = normalized.filter { it in '\u4e00'..'\u9fff' }
        // 中文 2-gram
        for (i in 0 until zh.length - 1) tokens += zh.substring(i, i + 2)
        return tokens
    }

    private fun vector(text: String): Map<String, Int> {
        termVecCache[text]?.let { return it }
        val v = mutableMapOf<String, Int>()
        tokenize(text).forEach { t -> v[t] = (v[t] ?: 0) + 1 }
        termVecCache[text] = v
        return v
    }

    private fun cosine(a: Map<String, Int>, b: Map<String, Int>): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for ((k, va) in a) {
            na += va * va
            val vb = b[k] ?: 0
            if (vb > 0) dot += va * vb
        }
        for ((_, vb) in b) nb += vb * vb
        return if (na > 0 && nb > 0) dot / kotlin.math.sqrt(na * nb) else 0.0
    }

    /** 检索与 query 最相关的知识条目（topK 条），返回拼好的文本 */
    fun retrieve(query: String, topK: Int = 3): String {
        if (query.isBlank()) return ""
        val qv = vector(query)
        val scored = entries.map { e ->
            val kw = vector(e.keywords.joinToString(" "))
            val titleScore = cosine(qv, kw)
            val contentScore = cosine(qv, vector(e.content))
            e to (titleScore * 0.6 + contentScore * 0.4)
        }.sortedByDescending { it.second }.take(topK).filter { it.second > 0.02 }
        if (scored.isEmpty()) return ""
        val sb = StringBuilder("【本地知识库参考（检索自内置分析知识）】\n")
        scored.forEach { (e, s) ->
            sb.append("• ${e.title}（相关度 ${com.dsa.app.util.Fmt.d(s)}）：${e.content}\n")
        }
        return sb.toString()
    }

    /** 内置知识条数（调试/关于页展示用） */
    val entryCount: Int get() = entries.size
}
