package dev.pocketdeck.adapter.persistence

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps a small secret unreadable at rest. */
internal interface SecretBox {
    fun seal(plain: String): String

    /** The secret, or null if it cannot be recovered (changed or damaged). Text that was never sealed is returned as it is. */
    fun open(stored: String): String?

    fun isSealed(stored: String): Boolean
}

/**
 * Encrypts with AES-GCM under a key that lives in the Android Keystore and never leaves it, so the stored text is of no use
 * outside this app on this phone. Every sealing uses a fresh nonce; a changed text fails to open.
 */
internal class KeystoreSecretBox(
    private val alias: String = DEFAULT_ALIAS,
) : SecretBox {
    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + encode(cipher.iv) + SEPARATOR + encode(encrypted)
    }

    override fun open(stored: String): String? {
        if (!isSealed(stored)) return stored
        return try {
            val (iv, encrypted) = stored.removePrefix(PREFIX).split(SEPARATOR).map(::decode)
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                }
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (failure: GeneralSecurityException) {
            null
        } catch (failure: IllegalArgumentException) {
            null
        }
    }

    override fun isSealed(stored: String) = stored.startsWith(PREFIX)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val DEFAULT_ALIAS = "pocketdeck_secrets"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "enc1:"
        const val SEPARATOR = ":"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
