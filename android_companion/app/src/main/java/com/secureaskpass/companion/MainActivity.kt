package com.secureaskpass.companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/**
 * Main activity: pairing UI and status display.
 * Scan QR code from askpass-manager to pair, then starts NtfyListenerService.
 */
class MainActivity : ComponentActivity() {

    private lateinit var pairingManager: PairingManager

    @androidx.camera.core.ExperimentalGetImage
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pairingManager = PairingManager(this)

        setContent {
            MaterialTheme {
                var showScanner by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        showScanner = true
                    } else {
                        Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                    }
                }

                if (showScanner) {
                    QrScannerScreen(
                        onQrScanned = { json ->
                            showScanner = false
                            doPair(json)
                        },
                        onCancel = { showScanner = false }
                    )
                } else {
                    MainScreen(
                        isPaired = pairingManager.isPaired(),
                        pairedHost = pairingManager.getPairedHost()?.hostname ?: "",
                        onScanQr = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                showScanner = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onManualPair = { json -> doPair(json) },
                        onUnpair = { doUnpair() }
                    )
                }
            }
        }

        // Start listener if already paired
        if (pairingManager.isPaired()) {
            startNtfyListener()
        }
    }

    private fun doPair(pairingJson: String) {
        thread {
            val success = pairingManager.completePairing(pairingJson)
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Paired", Toast.LENGTH_SHORT).show()
                    startNtfyListener()
                    recreate()
                } else {
                    Toast.makeText(this, "Pairing failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun doUnpair() {
        pairingManager.unpair()
        stopService(Intent(this, NtfyListenerService::class.java))
        recreate()
    }

    private fun startNtfyListener() {
        startService(Intent(this, NtfyListenerService::class.java))
    }
}

@Composable
fun MainScreen(
    isPaired: Boolean,
    pairedHost: String,
    onScanQr: () -> Unit,
    onManualPair: (String) -> Unit,
    onUnpair: () -> Unit
) {
    var manualInput by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Askpass Companion",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))

            if (isPaired) {
                Text("Paired to: $pairedHost")
                Text("Listening for sudo requests...")
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onUnpair) { Text("Unpair") }
            } else {
                Text("Not paired to any machine.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onScanQr) { Text("Scan QR Code") }
                Spacer(Modifier.height(16.dp))
                Text("Or paste pairing JSON:")
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onManualPair(manualInput) },
                    enabled = manualInput.isNotBlank()
                ) { Text("Pair") }
            }
        }
    }
}
