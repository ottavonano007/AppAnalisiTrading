package it.pietro.tradinganalysis.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the locally stored Alpha Vantage key with an Android Keystore AES-GCM key.
 * This protects casual access to app preferences; a key used by a client app cannot
 * be considered secret against a determined device owner or reverse engineering.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("trading_provider_credentials", Context.MODE_PRIVATE)

    fun save(value: String) {
        if (value.isBlank()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("alpha_key_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("alpha_key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String? = runCatching {
        val ciphertext = prefs.getString("alpha_key_ciphertext", null) ?: return null
        val iv = prefs.getString("alpha_key_iv", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        prefs.edit().remove("alpha_key_ciphertext").remove("alpha_key_iv").apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "analisi_trading_alpha_vantage_key"
    }
}
