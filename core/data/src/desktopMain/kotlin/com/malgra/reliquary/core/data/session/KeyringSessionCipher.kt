package com.malgra.reliquary.core.data.session

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * [AesGcmSessionCipher] with its key in the OS keychain: Keychain on macOS,
 * Credential Manager on Windows, Secret Service on Linux. Without a keychain
 * (e.g. Linux with no Secret Service) encrypting throws, so sign-in fails
 * rather than storing the session unprotected.
 */
class KeyringSessionCipher : AesGcmSessionCipher() {

    // Read once: each keychain read can prompt the user on macOS.
    private val key: SecretKey by lazy {
        val store = try {
            Keyring.create()
        } catch (e: Exception) {
            // Common on Linux desktops without GNOME Keyring or KWallet; say so rather than a bare library error.
            throw IllegalStateException("No OS keychain is available. On Linux, start a Secret Service such as GNOME Keyring or KWallet.", e)
        }
        store.use { keyring ->
            val stored = try {
                keyring.getPassword(SERVICE, ACCOUNT)
            } catch (_: PasswordAccessException) {
                null
            }
            stored?.let { SecretKeySpec(Base64.getDecoder().decode(it), "AES") }
                ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
                    keyring.setPassword(SERVICE, ACCOUNT, Base64.getEncoder().encodeToString(it.encoded))
                }
        }
    }

    override fun key(): SecretKey = key

    private companion object {
        const val SERVICE = "Reliquary"
        const val ACCOUNT = "session-key"
    }
}
