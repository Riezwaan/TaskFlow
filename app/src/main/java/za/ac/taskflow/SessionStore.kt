package za.ac.taskflow

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The password is never saved. Session tokens are encrypted with a device Keystore key. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("taskflow", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = prefs.getString("url", BuildConfig.API_URL)!!
        set(value) { prefs.edit().putString("url",value).apply() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("taskflow-session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("taskflow-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): String? = try {
        prefs.getString("session", null)?.let { encoded ->
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
                String(doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8)
            }
        }
    } catch (_: Exception) { clear(); null }
    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("session",Base64.encodeToString(bytes,Base64.NO_WRAP)).apply()
    }
    fun clear() { prefs.edit().remove("session").apply() }
}
