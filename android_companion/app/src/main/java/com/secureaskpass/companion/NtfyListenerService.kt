package com.secureaskpass.companion

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject

/**
 * Foreground service that subscribes to the ntfy topic via SSE
 * and launches AuthRequestActivity when a challenge arrives.
 */
class NtfyListenerService : Service() {

    private var eventSource: EventSource? = null
    private val client = OkHttpClient()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pairingManager = PairingManager(this)
        val pairedHost = pairingManager.getPairedHost()

        if (pairedHost == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        subscribeToNtfy(pairedHost.ntfyTopic, pairedHost.hostname, pairedHost.callbackPort)
        return START_STICKY
    }

    private fun subscribeToNtfy(topic: String, hostname: String, callbackPort: Int) {
        // Use SSE endpoint for real-time notifications
        val request = Request.Builder()
            .url("https://ntfy.sh/$topic/sse")
            .build()

        val factory = EventSources.createFactory(client)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                handleNotification(data, hostname, callbackPort)
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                Log.w("NtfyListener", "SSE connection failed, reconnecting...", t)
                // Reconnect after delay
                android.os.Handler(mainLooper).postDelayed({
                    subscribeToNtfy(topic, hostname, callbackPort)
                }, 5000)
            }
        })
    }

    private fun handleNotification(data: String, hostname: String, callbackPort: Int) {
        try {
            val message = JSONObject(data)
            val payload = message.optString("message", "")
            if (payload.isEmpty()) return

            val challengeData = JSONObject(payload)
            val challenge = challengeData.getString("challenge")
            val port = challengeData.optInt("callback_port", callbackPort)

            val challengeObj = JSONObject(challenge)
            val nonce = challengeObj.getString("nonce")
            val host = challengeObj.optString("host", hostname)
            val cmd = challengeObj.optString("cmd", "unknown")
            val user = challengeObj.optString("user", "unknown")

            // Launch auth request activity
            val intent = Intent(this, AuthRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("nonce", nonce)
                putExtra("host", host)
                putExtra("cmd", cmd)
                putExtra("user", user)
                putExtra("callback_host", host)
                putExtra("callback_port", port)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("NtfyListener", "Failed to parse notification", e)
        }
    }

    override fun onDestroy() {
        eventSource?.cancel()
        super.onDestroy()
    }
}
