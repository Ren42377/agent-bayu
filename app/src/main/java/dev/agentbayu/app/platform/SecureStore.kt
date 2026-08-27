package dev.agentbayu.app.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) : EncryptedStorage {

    private val directory = File(context.filesDir, DIRECTORY_NAME)
    private val lock = Any()

    override fun read(name: String): String? = synchronized(lock) {
        val file = File(directory, name)
        if (!file.exists()) return null
        return try {
            decrypt(file.readBytes())
        } catch (error: GeneralSecurityException) {
            Log.e(TAG, "Unable to decrypt " + name)
            file.delete()
            null
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Corrupted payload in " + name)
            file.delete()
            null
        }
    }

    override fun write(name: String, content: String) = synchronized(lock) {
        if (!directory.exists()) directory.mkdirs()
        val payload = encrypt(content)
        val temporary = File(directory, name + TEMP_SUFFIX)
        temporary.writeBytes(payload)
        val target = File(directory, name)
        if (!temporary.renameTo(target)) {
            target.writeBytes(payload)
            temporary.delete()
        }
    }

    override fun delete(name: String) {
        synchronized(lock) {
            File(directory, name).delete()
            File(directory, name + TEMP_SUFFIX).delete()
        }
    }

    private fun encrypt(content: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(content.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val output = ByteArray(1 + iv.size + encrypted.size)
        output[0] = FORMAT_VERSION
        System.arraycopy(iv, 0, output, 1, iv.size)
        System.arraycopy(encrypted, 0, output, 1 + iv.size, encrypted.size)
        return output
    }

    private fun decrypt(payload: ByteArray): String {
        require(payload.size > 1 + IV_LENGTH) { "payload too short" }
        require(payload[0] == FORMAT_VERSION) { "unsupported format" }
        val iv = payload.copyOfRange(1, 1 + IV_LENGTH)
        val body = payload.copyOfRange(1 + IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "SecureStore"
        private const val DIRECTORY_NAME = "secure"
        private const val TEMP_SUFFIX = ".tmp"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "agent_bayu_secure_store"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_SIZE_BITS = 256
        private const val FORMAT_VERSION: Byte = 1
    }
}
