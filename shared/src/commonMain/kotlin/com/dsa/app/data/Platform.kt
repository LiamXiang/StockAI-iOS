package com.dsa.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body

/** 各平台 Ktor 引擎 */
expect fun createHttpClient(): HttpClient

/** 腾讯行情接口返回 GBK 编码，各平台按原生方式解码 */
expect fun decodeGbk(bytes: ByteArray): String

/** 读取网络响应的原始字节（common API） */
suspend fun io.ktor.client.statement.HttpResponse.readBytes(): ByteArray = body<ByteArray>()

fun String?.orEmpty2(): String = this ?: ""
