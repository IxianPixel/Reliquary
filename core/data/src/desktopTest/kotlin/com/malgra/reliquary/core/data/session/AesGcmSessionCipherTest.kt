package com.malgra.reliquary.core.data.session

import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AesGcmSessionCipherTest {

    private class FixedKeyCipher(val key: SecretKey) : AesGcmSessionCipher() {
        override fun key() = key
    }

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun roundTripsWithAFreshIvEachTime() {
        val cipher = FixedKeyCipher(newKey())
        val plaintext = "session".encodeToByteArray()

        val first = cipher.encrypt(plaintext)
        val second = cipher.encrypt(plaintext)

        assertEquals(12, first[0].toInt())
        assertContentEquals(plaintext, cipher.decrypt(first))
        assertContentEquals(plaintext, cipher.decrypt(second))
        assert(!first.contentEquals(second))
    }

    @Test fun failsWithAnotherKey() {
        val sealed = FixedKeyCipher(newKey()).encrypt("session".encodeToByteArray())
        assertFails { FixedKeyCipher(newKey()).decrypt(sealed) }
    }
}
