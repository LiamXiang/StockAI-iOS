package com.dsa.app.data

/**
 * 内置密钥安全模块。
 * 密钥以「XOR + Base64」混淆存储（非明文），运行时解密注入到默认服务商配置。
 * 目的：防止密钥以明文形式出现在安装包/源码静态扫描中。
 * 说明：运行时内存中必然存在明文以便调用 API；本模块提供的是静态存储层的混淆保护。
 */
object Secrets {

    private const val SALT = "StockAI-Secure-Salt-2026"

    // 内置默认密钥（密文，勿改格式）。生成方式：XOR(明文, SALT 循环) 后 Base64。
    private val ENCRYPTED_DEFAULTS: Map<String, String> = mapOf(
        "mimo" to "JwRCABg5J1tjDgkCGA4bNQ8eHhteQ1FRJhsYAF8tPUwwVAlGBxNGZFQFFkhAQ0RaIUwd",
        "deepseek" to "IB9CAg95f0lqBlZHE1YUZ1YKRUwKAAJVZkJXWlkneho1VVA=",
        "stepfun" to "ZT1YDCVzLng2JwYgMCFkBQ4rM25rCWt3PBU+JAgOD2EUIw4nPw1OZzU+QBx5V3p4Hh02IRN2GFoaMDsvJhcfNzE=",
        "sensenova" to "IB9CLg0UKhoqKhIxOyZoHCUNQVpxXF9gMi08NzkWP1wbFAI=",
        "agnes" to "IB9CFgcTCmMVHSweRgZiYDA/IRxUeWMCODUWEy0HOB0UHC4jOT9iFlEbP2BERnQFCzMJ",
        "volcano" to "NhVcW15zLE9+B1sRQUgZZgVdWU8KUQUbZEUJV1t0LxQ1VFYW",
    )

    /** 内置默认密钥（解密后），providerId -> key */
    val builtinKeys: Map<String, String> = ENCRYPTED_DEFAULTS.mapValues { (_, v) -> decrypt(v) }

    /** 加密：明文 -> 混淆文本（用于用户自填 Key 的落盘存储） */
    fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        val saltBytes = saltBytes(plain.length)
        val out = ByteArray(plain.length)
        for (i in plain.indices) {
            out[i] = (plain[i].code xor saltBytes[i].toInt()).toByte()
        }
        return encodeBase64(out)
    }

    /** 解密：混淆文本 -> 明文 */
    fun decrypt(cipher: String): String {
        if (cipher.isBlank()) return ""
        val raw = decodeBase64(cipher)
        val salt = saltBytes(raw.size)
        val sb = StringBuilder(raw.size)
        for (i in raw.indices) {
            sb.append((raw[i].toInt() xor salt[i].toInt()).toChar())
        }
        return sb.toString()
    }

    private fun saltBytes(len: Int): ByteArray {
        val salt = SALT.encodeToByteArray()
        val out = ByteArray(len)
        for (i in 0 until len) out[i] = salt[i % salt.size]
        return out
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun encodeBase64(bytes: ByteArray): String =
        kotlin.io.encoding.Base64.encode(bytes)

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64(s: String): ByteArray =
        kotlin.io.encoding.Base64.decode(s)
}
