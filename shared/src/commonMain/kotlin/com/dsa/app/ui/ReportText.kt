package com.dsa.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * AI 报告轻量 Markdown 渲染：
 * - `## 标题` 渲染为加粗加大标题
 * - `**加粗**` 渲染为加粗
 * - 普通文本与列表原样展示
 * 让 AI 报告（个股/组合/持仓变化/历史）结构更清晰易读。
 */
@Composable
fun ReportText(
    report: String,
    modifier: Modifier = Modifier,
    baseSize: TextUnit = 13.sp,
) {
    val styled = remember(report, baseSize) { buildReportAnnotated(report, baseSize) }
    Text(styled, modifier = modifier, fontSize = baseSize)
}

private fun buildReportAnnotated(report: String, baseSize: TextUnit): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = report.split("\n")
    for ((idx, line) in lines.withIndex()) {
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("## ") -> {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseSize * 1.15f)) {
                    append(line.trim())
                }
            }
            trimmed.startsWith("# ") -> {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseSize * 1.3f)) {
                    append(line.trim())
                }
            }
            else -> appendInlineBold(builder, line, baseSize)
        }
        if (idx < lines.lastIndex) builder.append("\n")
    }
    return builder.toAnnotatedString()
}

/** 行内把 **加粗** 段渲染为粗体 */
private fun appendInlineBold(builder: AnnotatedString.Builder, line: String, baseSize: TextUnit) {
    val regex = Regex("""\*\*(.+?)\*\*""")
    val matches = regex.findAll(line)
    if (!matches.any()) {
        builder.append(line)
        return
    }
    var last = 0
    for (m in matches) {
        if (m.range.first > last) builder.append(line.substring(last, m.range.first))
        builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        last = m.range.last + 1
    }
    if (last < line.length) builder.append(line.substring(last))
}
