package com.malgra.reliquary.core.data.session

/**
 * Encrypts the stored session. Each platform keeps the key in its own secure
 * store (the Android Keystore, the OS keychain on desktop); tests use a fake.
 */
interface SessionCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Throws if the data can't be decrypted, for example after the key was lost. */
    fun decrypt(ciphertext: ByteArray): ByteArray
}
