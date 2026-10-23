package io.github.christiandonatti.fwip.remote

import com.hierynomus.security.Mac
import com.hierynomus.security.SecurityProvider
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.security.jce.JceSecurityProvider
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bouncy Castle for what Android lacks (MD4 for the NTLM login), the system's own crypto for
 * signing every packet. Bouncy Castle is plain Java; the system's AES runs on the processor's
 * crypto instructions, which matters when a NAS signs everything sent to it.
 */
internal class FastSecurityProvider : SecurityProvider by BCSecurityProvider() {
    private val jce = JceSecurityProvider()

    override fun getMac(name: String): Mac = when (name.uppercase()) {
        "AESCMAC" -> runCatching { NativeAesCmac().also { it.check() } }.getOrElse { BCSecurityProvider().getMac(name) }
        "HMACSHA256" -> runCatching { jce.getMac(name).also { it.init(ByteArray(16)); it.reset() } }.getOrElse { BCSecurityProvider().getMac(name) }
        else -> BCSecurityProvider().getMac(name)
    }
}

/** AES-CMAC (RFC 4493, used to sign SMB 3 packets) on top of the system's AES in CBC mode. */
internal class NativeAesCmac : Mac {
    private lateinit var key: SecretKeySpec
    private lateinit var k1: ByteArray
    private lateinit var k2: ByteArray
    private lateinit var cbc: Cipher
    private val pending = ByteArray(16)
    private var pendingLen = 0
    private val sink = ByteArray(64 * 1024)

    /** Fails early (falling back to Bouncy Castle) if the system has no AES in CBC mode. */
    fun check() {
        Cipher.getInstance("AES/CBC/NoPadding")
        Cipher.getInstance("AES/ECB/NoPadding")
    }

    override fun init(key: ByteArray) {
        this.key = SecretKeySpec(key, "AES")
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, this.key)
        val l = ecb.doFinal(ByteArray(16))
        k1 = double(l)
        k2 = double(k1)
        start()
    }

    private fun double(b: ByteArray): ByteArray {
        val out = ByteArray(16)
        var carry = 0
        for (i in 15 downTo 0) {
            val v = b[i].toInt() and 0xff
            out[i] = ((v shl 1) or carry).toByte()
            carry = v ushr 7
        }
        if ((b[0].toInt() and 0x80) != 0) out[15] = (out[15].toInt() xor 0x87).toByte()
        return out
    }

    private fun start() {
        cbc = Cipher.getInstance("AES/CBC/NoPadding")
        cbc.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(ByteArray(16)))
        pendingLen = 0
    }

    /** Runs whole blocks through the chain; the output is not needed, only the chain state. */
    private fun feed(array: ByteArray, offset: Int, length: Int) {
        var o = offset
        var left = length
        while (left > 0) {
            val n = minOf(left, sink.size)
            cbc.update(array, o, n, sink, 0)
            o += n
            left -= n
        }
    }

    override fun update(b: Byte) = update(byteArrayOf(b), 0, 1)

    override fun update(array: ByteArray) = update(array, 0, array.size)

    override fun update(array: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val total = pendingLen + length
        // The last block is treated differently, so up to 16 bytes always wait for doFinal.
        if (total <= 16) {
            System.arraycopy(array, offset, pending, pendingLen, length)
            pendingLen = total
            return
        }
        var toProcess = ((total - 1) / 16) * 16
        var o = offset
        var left = length
        if (pendingLen > 0) {
            val need = 16 - pendingLen
            System.arraycopy(array, o, pending, pendingLen, need)
            o += need
            left -= need
            feed(pending, 0, 16)
            toProcess -= 16
            pendingLen = 0
        }
        if (toProcess > 0) {
            feed(array, o, toProcess)
            o += toProcess
            left -= toProcess
        }
        System.arraycopy(array, o, pending, 0, left)
        pendingLen = left
    }

    override fun doFinal(): ByteArray {
        val last = ByteArray(16)
        System.arraycopy(pending, 0, last, 0, pendingLen)
        val sub = if (pendingLen == 16) k1 else {
            last[pendingLen] = 0x80.toByte()
            k2
        }
        for (i in 0 until 16) last[i] = (last[i].toInt() xor sub[i].toInt()).toByte()
        val out = cbc.doFinal(last)
        start()
        return out.copyOf(16)
    }

    override fun reset() = start()
}
