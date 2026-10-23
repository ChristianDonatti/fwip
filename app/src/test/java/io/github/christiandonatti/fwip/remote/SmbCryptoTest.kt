package io.github.christiandonatti.fwip.remote

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.params.KeyParameter
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

/** The system-crypto AES-CMAC must sign exactly like Bouncy Castle's, however the data arrives. */
class SmbCryptoTest {
    private fun reference(key: ByteArray, data: ByteArray): ByteArray {
        val mac = CMac(AESEngine())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(16).also { mac.doFinal(it, 0) }
    }

    @Test
    fun rfc4493Example() {
        val key = "2b7e151628aed2a6abf7158809cf4f3c".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val msg = "6bc1bee22e409f96e93d7e117393172a".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val mac = NativeAesCmac().apply { init(key) }
        mac.update(msg)
        val expected = "070a16b46b4d4144f79bdd9dd04a287c".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, mac.doFinal())
    }

    @Test
    fun matchesBouncyCastle() {
        val r = Random(7)
        val mac = NativeAesCmac()
        for (len in listOf(0, 1, 15, 16, 17, 31, 32, 33, 64, 100, 4096, 70_000, 1 shl 20)) {
            repeat(3) {
                val key = r.nextBytes(16)
                val data = r.nextBytes(len)
                mac.init(key)
                // Fed in uneven pieces, the way packets are signed.
                var o = 0
                while (o < len) {
                    val n = minOf(len - o, 1 + r.nextInt(5000))
                    if (n == 1) mac.update(data[o]) else mac.update(data, o, n)
                    o += n
                }
                assertArrayEquals("length $len", reference(key, data), mac.doFinal())
                // And again after doFinal, with the same key.
                mac.update(data)
                assertArrayEquals("again $len", reference(key, data), mac.doFinal())
            }
        }
    }
}
