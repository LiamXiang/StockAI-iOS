package com.dsa.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataWithBytes

/** NSGBKStringEncoding = 0x80000632（Kotlin/Native 未导出该常量，直接给数值） */
private const val GBK_ENCODING = 0x80000632u

actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 180_000
        socketTimeoutMillis = 180_000
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun decodeGbk(bytes: ByteArray): String {
    val data = bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    }
    return NSString.create(data = data, encoding = GBK_ENCODING)?.toString() ?: ""
}
