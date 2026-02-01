package com.secureaskpass.companion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlin.concurrent.thread

/**
 * Displays sudo request details and triggers BiometricPrompt.
 * Launched by NtfyListenerService when a challenge notification arrives.
 */
class AuthRequestActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nonce = intent.getStringExtra("nonce") ?: run { finish(); return }
        val host = intent.getStringExtra("host") ?: "unknown"
        val cmd = intent.getStringExtra("cmd") ?: "unknown"
        val user = intent.getStringExtra("user") ?: "unknown"
        val callbackHost = intent.getStringExtra("callback_host") ?: host
        val callbackPort = intent.getIntExtra("callback_port", 8765)

        setContent {
            MaterialTheme {
                AuthRequestScreen(
                    host = host,
                    user = user,
                    cmd = cmd,
                    onApprove = {
                        triggerBiometric(nonce, callbackHost, callbackPort)
                    },
                    onDeny = { finish() }
                )
            }
        }

        // Auto-trigger biometric
        triggerBiometric(nonce, callbackHost, callbackPort)
    }

    private fun triggerBiometric(nonce: String, callbackHost: String, callbackPort: Int) {
        val host = intent.getStringExtra("host") ?: "unknown"
        val cmd = intent.getStringExtra("cmd") ?: "unknown"

        BiometricHelper.authenticate(
            activity = this,
            title = "Sudo Authentication",
            subtitle = "$host: $cmd",
            onSuccess = {
                thread {
                    val signature = CryptoManager.signChallenge(nonce)
                    val ok = HttpCallbackClient.sendAuthResponse(
                        callbackHost, callbackPort, nonce, signature
                    )
                    runOnUiThread {
                        if (ok) {
                            Toast.makeText(this, "Authorized", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to send response", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                }
            },
            onFailure = { error ->
                Toast.makeText(this, "Auth failed: $error", Toast.LENGTH_SHORT).show()
                finish()
            }
        )
    }
}

@Composable
fun AuthRequestScreen(
    host: String,
    user: String,
    cmd: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sudo Authentication",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            Text("Host: $host")
            Text("User: $user")
            Text("Command: $cmd")
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onDeny) { Text("Deny") }
                Button(onClick = onApprove) { Text("Approve") }
            }
        }
    }
}
