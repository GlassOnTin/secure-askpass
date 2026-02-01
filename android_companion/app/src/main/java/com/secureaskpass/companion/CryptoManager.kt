package com.secureaskpass.companion

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.Base64

/**
 * Manages Ed25519 key pair in Android Keystore for signing challenge nonces.
 * Keys are hardware-backed on supported devices.
 */
object CryptoManager {

    private const val KEYSTORE_ALIAS = "askpass_auth_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun generateKeyPair() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return // Already exists
        }

        // API 33+ supports Ed25519 in KeyStore; fall back to EC for older
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_NONE)
            .setAlgorithmParameterSpec(
                java.security.spec.ECGenParameterSpec("secp256r1")
            )
            .setUserAuthenticationRequired(false) // Biometric is checked separately
            .build()

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    fun getPublicKeyPem(): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val publicKey = keyStore.getCertificate(KEYSTORE_ALIAS).publicKey
        val encoded = Base64.getEncoder().encodeToString(publicKey.encoded)
        return "-----BEGIN PUBLIC KEY-----\n" +
                encoded.chunked(64).joinToString("\n") +
                "\n-----END PUBLIC KEY-----"
    }

    fun signChallenge(nonce: String): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null) as java.security.PrivateKey

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(nonce.toByteArray(Charsets.UTF_8))
        val signed = signature.sign()
        return Base64.getEncoder().encodeToString(signed)
    }

    fun hasKeyPair(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.containsAlias(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    fun deleteKeyPair() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        keyStore.deleteEntry(KEYSTORE_ALIAS)
    }
}
