package com.malgra.reliquary.core.data.session

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM; platforms differ only in where the [key] is kept. Output is `ivLength | iv | ciphertext`. */
abstract class AesGcmSessionCipher : SessionCipher {

    protected abstract fun key(): SecretKey

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val ivLength = ciphertext[0].toInt()
        val iv = ciphertext.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(ciphertext, 1 + ivLength, ciphertext.size - 1 - ivLength)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
