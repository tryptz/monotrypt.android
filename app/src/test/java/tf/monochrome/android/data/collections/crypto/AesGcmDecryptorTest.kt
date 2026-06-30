package tf.monochrome.android.data.collections.crypto

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmDecryptorTest {
    private val decryptor = AesGcmDecryptor()

    @Test
    fun decryptsAesGcmPayloadWithPrependedIv() {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(12) { (it + 16).toByte() }
        val plaintext = "https://cdn.example.test/stream.flac".toByteArray()
        val encrypted = encryptWithPrependedIv(plaintext, key, iv)

        assertArrayEquals(plaintext, decryptor.decrypt(encrypted, key))
    }

    @Test
    fun rejectsTamperedCiphertext() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val iv = ByteArray(12) { (it + 32).toByte() }
        val encrypted = encryptWithPrependedIv("secret url".toByteArray(), key, iv)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) {
            decryptor.decrypt(encrypted, key)
        }
    }

    @Test
    fun plaintextUrlsPassThroughWithoutKeyMaterial() {
        assertEquals(
            "https://cdn.example.test/plain.flac",
            decryptor.decryptUrl("https://cdn.example.test/plain.flac", "not-a-key"),
        )
    }

    private fun encryptWithPrependedIv(
        plaintext: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext)
    }
}
