package com.dsa.app.util

import com.dsa.app.util.nowMs

import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

/**
 * 跨平台数字/日期格式化。
 * iOS（Kotlin/Native）没有 java.lang.String.format / java.lang.Math，
 * 统一走这里避免平台差异。
 */
object Fmt {

    /** 保留 decimals 位小数（默认2位），NaN/Infinity 返回 "—" */
    fun d(v: Double, decimals: Int = 2): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        val r = round(v * factor) / factor
        val s = r.toString()
        if (decimals <= 0) return s.substringBefore('.')
        val dot = s.indexOf('.')
        val intPart = if (dot >= 0) s.substring(0, dot) else s
        val fracPart = if (dot >= 0) s.substring(dot + 1) else ""
        return if (fracPart.length < decimals) {
            intPart + "." + fracPart + "0".repeat(decimals - fracPart.length)
        } else {
            intPart + "." + fracPart
        }
    }

    /** 带符号（正数前加 +） */
    fun s(v: Double, decimals: Int = 2): String {
        val sign = if (v > 0) "+" else ""
        return sign + d(v, decimals)
    }

    private fun p2(n: Int): String = if (n < 10) "0$n" else n.toString()

    /** 日期 yyyy-MM-dd */
    fun date(ms: Long): String {
        val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        return "${dt.year}-${p2(dt.monthNumber)}-${p2(dt.dayOfMonth)}"
    }

    /** 日期 yyyy-MM-dd（年月日直接传入） */
    fun ymd(y: Int, m: Int, d: Int): String = "$y-${p2(m)}-${p2(d)}"

    /** 日期时间 yyyy-MM-dd HH:mm */
    fun ymdhm(y: Int, m: Int, d: Int, h: Int, mi: Int): String = "$y-${p2(m)}-${p2(d)} ${p2(h)}:${p2(mi)}"

    /** 日期时间 yyyy-MM-dd HH:mm */
    fun time(ms: Long): String {
        val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        return "${dt.year}-${p2(dt.monthNumber)}-${p2(dt.dayOfMonth)} ${p2(dt.hour)}:${p2(dt.minute)}"
    }
}

/** 当前毫秒时间戳（跨平台，替代 System.currentTimeMillis） */
fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
