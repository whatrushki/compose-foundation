package app.what.foundation.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidPreferenceEncryptor(
    private val keyAlias: String = "what_preferences_key"
) : PreferenceEncryptor {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: error("Secret key not found for alias $keyAlias")
    }

    override fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            "enc_v2:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt preference value", e)
        }
    }

    override fun decrypt(cipherText: String): String {
        if (cipherText.startsWith("enc_v2:")) {
            return try {
                val raw = Base64.decode(cipherText.removePrefix("enc_v2:"), Base64.NO_WRAP)
                if (raw.isEmpty()) return cipherText
                val ivSize = raw[0].toInt()
                if (raw.size < 1 + ivSize) return cipherText
                val iv = ByteArray(ivSize)
                val encryptedSize = raw.size - 1 - ivSize
                val encrypted = ByteArray(encryptedSize)
                System.arraycopy(raw, 1, iv, 0, ivSize)
                System.arraycopy(raw, 1 + ivSize, encrypted, 0, encryptedSize)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to decrypt secure preference (v2)", e)
            }
        }

        if (cipherText.startsWith("enc:")) {
            return try {
                val rawEncrypted = cipherText.removePrefix("enc:")
                val combined = Base64.decode(rawEncrypted, Base64.NO_WRAP)
                val ivSize = 12
                if (combined.size < ivSize) {
                    return cipherText
                }
                val iv = ByteArray(ivSize)
                val encrypted = ByteArray(combined.size - ivSize)
                System.arraycopy(combined, 0, iv, 0, ivSize)
                System.arraycopy(combined, ivSize, encrypted, 0, encrypted.size)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
                val decrypted = cipher.doFinal(encrypted)
                String(decrypted, Charsets.UTF_8)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to decrypt secure preference (legacy)", e)
            }
        }

        return cipherText
    }
}
