package com.dsa.app.data

import kotlinx.serialization.Serializable

/** 腾讯实时行情（qt.gtimg.cn）解析结果 */
data class Quote(
    val code: String,        // 600519
    val name: String,        // 贵州茅台
    val price: Double,       // 现价
    val prevClose: Double,   // 昨收
    val open: Double,        // 今开
    val high: Double,        // 最高
    val low: Double,         // 最低
    val change: Double,      // 涨跌额
    val changePct: Double,   // 涨跌幅 %
    val volume: Long,        // 成交量（手）
    val amount: Double,      // 成交额（万元）
    val turnover: Double,    // 换手率 %
    val pe: Double,          // 市盈率
    val pb: Double,          // 市净率
    val marketCap: Double,   // 总市值（亿）
    val floatCap: Double,    // 流通市值（亿）
    val amplitude: Double,   // 振幅 %
    val time: String,        // 行情时间
) {
    val isUp: Boolean get() = change >= 0
    val isLimitUp: Boolean get() = price >= limitUp
    val limitUp: Double get() = round2(prevClose * 1.10)
    val limitDown: Double get() = round2(prevClose * 0.90)

    companion object {
        fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0
    }
}

/** 新浪 K 线数据（quotes.sina.cn getKLineData） */
@Serializable
data class SinaKline(
    val day: String,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
) {
    val openV: Double get() = open.toDoubleOrNull() ?: 0.0
    val highV: Double get() = high.toDoubleOrNull() ?: 0.0
    val lowV: Double get() = low.toDoubleOrNull() ?: 0.0
    val closeV: Double get() = close.toDoubleOrNull() ?: 0.0
    val volumeV: Double get() = volume.toDoubleOrNull() ?: 0.0
}

/** 归一化 K 线 */
data class KlinePoint(
    val day: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

/** 股票搜索建议（腾讯 smartbox） */
data class StockSuggestion(
    val code: String,
    val name: String,
    val market: String, // sh / sz
    val type: String,   // gp=股票 zs=指数
)

/** 硅基流动 Chat 请求/响应 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 2048,
    val temperature: Double = 0.5,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: ApiError? = null,
)

@Serializable
data class Choice(
    val message: ChatMessage,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val code: String? = null,
)

/** 指标计算结果 */
data class IndicatorResult(
    val ma5: DoubleArray? = null,
    val ma10: DoubleArray? = null,
    val ma20: DoubleArray? = null,
    val ma60: DoubleArray? = null,
    val dif: DoubleArray? = null,   // MACD DIF
    val dea: DoubleArray? = null,   // MACD DEA
    val macd: DoubleArray? = null,  // MACD 柱
    val k: DoubleArray? = null,     // KDJ K
    val d: DoubleArray? = null,     // KDJ D
    val j: DoubleArray? = null,     // KDJ J
    val rsi6: DoubleArray? = null,
    val rsi12: DoubleArray? = null,
    val rsi24: DoubleArray? = null,
    val bollMid: DoubleArray? = null,
    val bollUp: DoubleArray? = null,
    val bollLow: DoubleArray? = null,
    // 特色指标
    val ema12: DoubleArray? = null,  // EMA12
    val ema26: DoubleArray? = null,  // EMA26
    val wr: DoubleArray? = null,      // 威廉指标 WR(14)
    val cci: DoubleArray? = null,     // 顺势指标 CCI(14)
    val adx: DoubleArray? = null,     // DMI ADX(14)
    val pdi: DoubleArray? = null,     // DMI +DI
    val mdi: DoubleArray? = null,     // DMI -DI
    val bias6: DoubleArray? = null,   // 乖离率 BIAS6
    val bias12: DoubleArray? = null,  // 乖离率 BIAS12
    val sar: DoubleArray? = null,     // 抛物线 SAR
    val obv: DoubleArray? = null,     // 能量潮 OBV
    val roc: DoubleArray? = null,     // 变动率 ROC(12)
    // 新增特色指标
    val atr: DoubleArray? = null,     // 平均真实波幅 ATR(14)
    val tr: DoubleArray? = null,      // 真实波幅 TR
    val stochK: DoubleArray? = null,  // 随机指标 K
    val stochD: DoubleArray? = null,  // 随机指标 D
    val mfi: DoubleArray? = null,     // 资金流量指数 MFI(14)
    val vwap: DoubleArray? = null,    // 成交量加权平均价 VWAP
    val tsi: DoubleArray? = null,     // 真实强度指数 TSI
    val trix: DoubleArray? = null,    // 三重指数平均 TRIX
    val ultimate: DoubleArray? = null,// 终极振荡器 UO
    val ao: DoubleArray? = null,      // 动量震荡 AO
    val cmf: DoubleArray? = null,     // 佳庆资金流 CMF(20)
    val keltnerMid: DoubleArray? = null,  // 肯特纳通道中轨
    val keltnerUp: DoubleArray? = null,   // 肯特纳通道上轨
    val keltnerLow: DoubleArray? = null,  // 肯特纳通道下轨
    val donchianUp: DoubleArray? = null,  // 唐奇安通道上轨
    val donchianLow: DoubleArray? = null, // 唐奇安通道下轨
    val superTrend: DoubleArray? = null,  // 超级趋势
    val superTrendDir: DoubleArray? = null, // 超级趋势方向(1多-1空)
    val ichimokuTenkan: DoubleArray? = null,   // 一目均衡表 转换线
    val ichimokuKijun: DoubleArray? = null,    // 一目均衡表 基准线
    val ichimokuSenkouA: DoubleArray? = null,  // 一目均衡表 先行带A
    val ichimokuSenkouB: DoubleArray? = null,  // 一目均衡表 先行带B
    val rvi: DoubleArray? = null,     // 相对活力指数 RVI
    val coppock: DoubleArray? = null, // 科普曲线 Coppock
    val ema9: DoubleArray? = null,    // EMA9
    val ema21: DoubleArray? = null,   // EMA21
    val ema50: DoubleArray? = null,   // EMA50
    val wma20: DoubleArray? = null,   // 加权移动平均 WMA20
    val hma20: DoubleArray? = null,   // 赫尔移动平均 HMA20
    val kama20: DoubleArray? = null,  // 考夫曼自适应移动平均 KAMA20
    val tema20: DoubleArray? = null,  // 三重指数移动平均 TEMA20
    val williamsR: DoubleArray? = null, // 威廉指标(别名)
    val ppo: DoubleArray? = null,     // 百分比价格振荡器 PPO
    val qstick: DoubleArray? = null,  // Qstick
    val fisher: DoubleArray? = null,  // Fisher变换
)

/** AI 分析报告（持久化用） */
@Serializable
data class AnalysisReport(
    val id: Long = System.currentTimeMillis(),
    val code: String,
    val name: String,
    val content: String,
    val model: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** 持仓股（手动录入 / OCR 导入） */
@Serializable
data class Holding(
    val code: String,
    val shares: Int = 0,
    val costPrice: Double = 0.0,
)

/** 持仓账号（多账号支持） */
@Serializable
data class HoldingAccount(
    val id: String = "account_1",
    val name: String = "账号1",
    val holdings: List<Holding> = emptyList(),
)

/** 组合分析报告（持久化用） */
@Serializable
data class PortfolioReport(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val model: String = "",
    val stockCount: Int = 0,
    val totalValue: Double = 0.0,
    val accountId: String = "account_1",
    val accountName: String = "账号1",
    val createdAt: Long = System.currentTimeMillis(),
)

/** 持仓快照（用于跟踪持仓变化） */
@Serializable
data class HoldingSnapshot(
    val id: Long = System.currentTimeMillis(),
    val holdings: List<Holding>,
    val source: String = "manual", // manual / ocr / edit
    val accountId: String = "account_1",
    val createdAt: Long = System.currentTimeMillis(),
)

/** 持仓变化分析报告（持久化用） */
@Serializable
data class HoldingChangeReport(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val model: String = "",
    val oldSnapshotId: Long = 0,
    val newSnapshotId: Long = 0,
    val oldSnapshotTime: Long = 0,
    val newSnapshotTime: Long = 0,
    val accountId: String = "account_1",
    val accountName: String = "账号1",
    val createdAt: Long = System.currentTimeMillis(),
)

/** AI 服务商配置 */
data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val modelsEndpoint: String = "/models",
    val compatible: Boolean = true, // 是否OpenAI兼容
)

/** AI 模型列表响应 */
@Serializable
data class ModelsResponse(
    val data: List<ModelItem>? = null,
)

@Serializable
data class ModelItem(
    val id: String = "",
    val owned_by: String? = null,
)
