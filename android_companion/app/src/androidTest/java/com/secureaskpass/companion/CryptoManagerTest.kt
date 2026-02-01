package com.secureaskpass.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class CryptoManagerTest {

    @Before
    fun setUp() {
        // Clean slate
        if (CryptoManager.hasKeyPair()) {
            CryptoManager.deleteKeyPair()
        }
    }

    @After
    fun tearDown() {
        if (CryptoManager.hasKeyPair()) {
            CryptoManager.deleteKeyPair()
        }
    }

    @Test
    fun testGenerateKeyPair_createsKey() {
        CryptoManager.generateKeyPair()
        assertTrue("Key pair should exist after generation", CryptoManager.hasKeyPair())
    }

    @Test
    fun testGenerateKeyPair_idempotent() {
        CryptoManager.generateKeyPair()
        CryptoManager.generateKeyPair() // second call should not throw
        assertTrue(CryptoManager.hasKeyPair())
    }

    @Test
    fun testGetPublicKeyPem_format() {
        CryptoManager.generateKeyPair()
        val pem = CryptoManager.getPublicKeyPem()
        assertTrue("PEM should start with header", pem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue("PEM should end with footer", pem.trimEnd().endsWith("-----END PUBLIC KEY-----"))
    }

    @Test
    fun testSignChallenge_producesValidBase64() {
        CryptoManager.generateKeyPair()
        val sig = CryptoManager.signChallenge("test-nonce-123")
        // Should not throw on decode
        val decoded = Base64.getDecoder().decode(sig)
        assertTrue("Signature should have non-zero length", decoded.isNotEmpty())
    }

    @Test
    fun testDeleteKeyPair() {
        CryptoManager.generateKeyPair()
        assertTrue(CryptoManager.hasKeyPair())
        CryptoManager.deleteKeyPair()
        assertFalse("Key pair should not exist after deletion", CryptoManager.hasKeyPair())
    }

    @Test
    fun testSignChallenge_verifiableWithPublicKey() {
        CryptoManager.generateKeyPair()
        val nonce = "verify-me-nonce"
        val sigB64 = CryptoManager.signChallenge(nonce)
        val sigBytes = Base64.getDecoder().decode(sigB64)

        // Parse public key from PEM
        val pem = CryptoManager.getPublicKeyPem()
        val base64Key = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
        val keyBytes = Base64.getDecoder().decode(base64Key)
        val pubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))

        // Verify signature
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pubKey)
        verifier.update(nonce.toByteArray(Charsets.UTF_8))
        assertTrue("Signature should verify with own public key", verifier.verify(sigBytes))
    }
}
