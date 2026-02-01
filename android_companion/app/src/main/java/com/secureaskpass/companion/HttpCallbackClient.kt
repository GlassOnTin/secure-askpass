package com.secureaskpass.companion

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends signed challenge response back to the Linux callback server.
 */
object HttpCallbackClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun sendAuthResponse(
        host: String,
        port: Int,
        nonce: String,
        signature: String
    ): Boolean {
        val json = JSONObject().apply {
            put("nonce", nonce)
            put("signature", signature)
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("http://$host:$port/auth/response")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
