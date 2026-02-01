package com.secureaskpass.companion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairingManagerTest {

    private lateinit var pairingManager: PairingManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        pairingManager = PairingManager(context)
        // Clean state
        pairingManager.unpair()
    }

    @After
    fun tearDown() {
        pairingManager.unpair()
    }

    @Test
    fun testIsPaired_initiallyFalse() {
        assertFalse("Should not be paired initially", pairingManager.isPaired())
    }

    @Test
    fun testUnpair_clearsPairingAndKey() {
        // Generate a key to simulate pairing side-effect
        CryptoManager.generateKeyPair()
        assertTrue("Key should exist before unpair", CryptoManager.hasKeyPair())

        pairingManager.unpair()

        assertFalse("Should not be paired after unpair", pairingManager.isPaired())
        assertFalse("Key should be deleted after unpair", CryptoManager.hasKeyPair())
    }
}
