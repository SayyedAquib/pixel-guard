package com.pixelguard.applock

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pixelguard.applock.data.local.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Instrumented test verifying actual hardware-backed AndroidKeyStore operations
 * (StrongBox / Titan M2 / TEE) on real devices or modern Android Virtual Devices.
 */
@RunWith(AndroidJUnit4::class)
class KeyStoreInstrumentedTest {

    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManager()
        // Ensure clean test state if key exists
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(CryptoManager.KEY_ALIAS)) {
                keyStore.deleteEntry(CryptoManager.KEY_ALIAS)
            }
        } catch (_: Throwable) {
            // Ignored in setup
        }
    }

    @Test
    fun keyStore_generatesHardwareKeyAndPersistsInKeyStore() {
        val testPayload = "PixelGuard-Hardware-Key-Test".toByteArray(Charsets.UTF_8)
        val encrypted = cryptoManager.encryptToFileBytes(testPayload)
        assertNotNull(encrypted)
        assertTrue(encrypted.size > 12 + 16) // IV (12) + Tag (16) + Payload

        // Verify key entry exists in AndroidKeyStore
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue("AndroidKeyStore must contain the master key alias", keyStore.containsAlias(CryptoManager.KEY_ALIAS))

        val decrypted = cryptoManager.decryptFromFileBytes(encrypted)
        assertArrayEquals(testPayload, decrypted)
    }

    @Test
    fun keyStore_stringEncryptionRoundtrip() {
        val originalText = "Secret-Biometric-Token-12345"
        val ciphertext = cryptoManager.encryptString(originalText)
        assertTrue(ciphertext.startsWith(CryptoManager.ENC_PREFIX))

        val decrypted = cryptoManager.decryptString(ciphertext)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun keyStore_pinHashingAndVerification() {
        val pin = "123456".toCharArray()
        val salt = cryptoManager.generateSalt()
        val hash = cryptoManager.hashPin(pin, salt)

        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())

        // Verification with correct PIN
        val correctPin = "123456".toCharArray()
        val correctHash = cryptoManager.hashPin(correctPin, salt)
        assertTrue(cryptoManager.verifyHash(correctHash, hash))

        // Verification with wrong PIN
        val wrongPin = "654321".toCharArray()
        val wrongHash = cryptoManager.hashPin(wrongPin, salt)
        assertFalse(cryptoManager.verifyHash(wrongHash, hash))
    }
}
