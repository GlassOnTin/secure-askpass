package com.secureaskpass.companion

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages pairing state with Linux machines.
 * Stores pairing info in encrypted SharedPreferences.
 */
class PairingManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "askpass_pairing",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    data class PairedHost(
        val hostname: String,
        val ntfyTopic: String,
        val callbackPort: Int
    )

    fun isPaired(): Boolean = prefs.contains("ntfy_topic")

    fun getPairedHost(): PairedHost? {
        val topic = prefs.getString("ntfy_topic", null) ?: return null
        return PairedHost(
            hostname = prefs.getString("hostname", "unknown") ?: "unknown",
            ntfyTopic = topic,
            callbackPort = prefs.getInt("callback_port", 8765)
        )
    }

    fun completePairing(pairingJson: String): Boolean {
        return try {
            val data = JSONObject(pairingJson)
            val ntfyTopic = data.getString("ntfy_topic")
            val callbackPort = data.getInt("callback_port")
            val hostname = data.getString("host")

            // Generate key pair if needed
            CryptoManager.generateKeyPair()
            val pubkeyPem = CryptoManager.getPublicKeyPem()

            // Send public key to Linux host
            val deviceName = android.os.Build.MODEL
            val pairPayload = JSONObject().apply {
                put("name", deviceName)
                put("pubkey", pubkeyPem)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()

            // The Linux pairing server listens on the callback port
            val body = pairPayload.toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("http://$hostname:$callbackPort/pair")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            // Save pairing info
            prefs.edit()
                .putString("ntfy_topic", ntfyTopic)
                .putString("hostname", hostname)
                .putInt("callback_port", callbackPort)
                .apply()

            true
        } catch (e: Exception) {
            false
        }
    }

    fun unpair() {
        prefs.edit().clear().apply()
        if (CryptoManager.hasKeyPair()) {
            CryptoManager.deleteKeyPair()
        }
    }
}
