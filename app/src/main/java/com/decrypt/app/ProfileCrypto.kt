package com.decrypt.app

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * 极速网络（com.nexgen.jisuwangluo）落在 profiles/ 里的 yaml 用的是
 * FlClash 的 "FLCLASH_ENCRYPTED_V1" 容器：
 *
 *     FLCLASH_ENCRYPTED_V1:<base64( 明文 XOR 重复密钥 )>
 *
 * 密钥就是解密的那个脚本爆破出来的这一串。
 */
object ProfileCrypto {

    const val PREFIX = "FLCLASH_ENCRYPTED_V1:"
    private val KEY = "FlClash2024SecretKey!@#$%^&*()".toByteArray(Charsets.US_ASCII)
    private val NON_B64 = Regex("[^A-Za-z0-9+/=]")

    /** 解密失败（不是加密文件 / 内容坏了）返回 null */
    fun decrypt(raw: ByteArray): String? {
        val text = raw.toString(Charsets.UTF_8).trim()
        if (!text.startsWith(PREFIX)) return null

        val body = NON_B64.replace(text.substring(PREFIX.length), "")
        if (body.isEmpty()) return null

        val data = try {
            Base64.decode(body, Base64.DEFAULT)
        } catch (e: Exception) {
            return null
        }
        if (data.size < 32) return null

        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        }

        val plain = decodeUtf8(out) ?: return null
        val looksLikeClash = plain.contains("proxies:") ||
            plain.contains("proxy-groups:") ||
            plain.contains("mixed-port")
        return if (looksLikeClash) plain else null
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: Exception) {
        null
    }
}
