package com.pixelguard.applock.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManager()
    }

    @Test
    fun generateSalt_returns16ByteRandomSalt() {
        val salt1 = cryptoManager.generateSalt()
        val salt2 = cryptoManager.generateSalt()

        assertNotNull(salt1)
        assertEquals(16, salt1.size)
        assertEquals(16, salt2.size)
        // Two cryptographically secure random salts should not match
        assertFalse(salt1.contentEquals(salt2))
    }

    @Test
    fun hashPin_produces64CharHexSha256String() {
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val hash = cryptoManager.hashPin("1234", salt)

        assertNotNull(hash)
        assertEquals(64, hash.length)
        // Must contain only lowercase hex characters
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun hashPin_withSameInputAndSalt_producesDeterministicHash() {
        val salt = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
        val hash1 = cryptoManager.hashPin("9876", salt)
        val hash2 = cryptoManager.hashPin("9876", salt)

        assertEquals(hash1, hash2)
    }

    @Test
    fun hashPin_withDifferentSalts_producesDifferentHashes() {
        val salt1 = byteArrayOf(1, 2, 3, 4)
        val salt2 = byteArrayOf(5, 6, 7, 8)
        val pin = "1234"

        val hash1 = cryptoManager.hashPin(pin, salt1)
        val hash2 = cryptoManager.hashPin(pin, salt2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun hashPin_withDifferentPins_producesDifferentHashes() {
        val salt = byteArrayOf(1, 2, 3, 4)
        val hash1 = cryptoManager.hashPin("1234", salt)
        val hash2 = cryptoManager.hashPin("1235", salt)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun verifyHash_withMatchingHashes_returnsTrue() {
        val salt = cryptoManager.generateSalt()
        val hash = cryptoManager.hashPin("4321", salt)

        assertTrue(cryptoManager.verifyHash(hash, hash))
    }

    @Test
    fun verifyHash_withMismatchedHashes_returnsFalse() {
        val salt = cryptoManager.generateSalt()
        val hash1 = cryptoManager.hashPin("1111", salt)
        val hash2 = cryptoManager.hashPin("2222", salt)

        assertFalse(cryptoManager.verifyHash(hash1, hash2))
    }

    @Test
    fun verifyHash_withDifferentLengthStrings_returnsFalse() {
        assertFalse(cryptoManager.verifyHash("abcdef", "abcde"))
        assertFalse(cryptoManager.verifyHash("", "abcde"))
    }

    @Test
    fun hashPin_underArabicLocale_producesAsciiHexOnly() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ar", "EG"))
            val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
            val hash = cryptoManager.hashPin("123456", salt)
            assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
            assertEquals(64, hash.length)
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun hexEncoding_andDecoding_roundtripsCorrectly() {
        val bytes = byteArrayOf(0x00, 0x0F, 0x1A, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val hex = cryptoManager.byteArrayToHex(bytes)
        assertEquals("000f1a7f80ff", hex)
        val decoded = cryptoManager.hexToByteArray(hex, expectedByteCount = 6)
        assertTrue(bytes.contentEquals(decoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun hexToByteArray_withInvalidLength_throws() {
        cryptoManager.hexToByteArray("abc") // Odd length
    }

    @Test(expected = IllegalArgumentException::class)
    fun hexToByteArray_withNonHexChars_throws() {
        cryptoManager.hexToByteArray("0011zz")
    }

    @Test(expected = IllegalArgumentException::class)
    fun hexToByteArray_withWrongByteCount_throws() {
        cryptoManager.hexToByteArray("001122", expectedByteCount = 4)
    }

    @Test
    fun isWeakPin_correctlyIdentifiesTrivialPatterns() {
        assertTrue(cryptoManager.isWeakPin("000000"))
        assertTrue(cryptoManager.isWeakPin("111111"))
        assertTrue(cryptoManager.isWeakPin("123456"))
        assertTrue(cryptoManager.isWeakPin("654321"))
        assertTrue(cryptoManager.isWeakPin("121212"))
        assertTrue(cryptoManager.isWeakPin("123123"))
        assertTrue(cryptoManager.isWeakPin("1234")) // Not 6 digits
        assertTrue(cryptoManager.isWeakPin("1234567")) // Not 6 digits

        // Strong non-patterned 6-digit PINs
        assertFalse(cryptoManager.isWeakPin("948201"))
        assertFalse(cryptoManager.isWeakPin("381940"))
        assertFalse(cryptoManager.isWeakPin("710538"))
    }

    @Test
    fun generateRecoveryCode_producesFormatted16CharBase32Code() {
        val code1 = cryptoManager.generateRecoveryCode()
        val code2 = cryptoManager.generateRecoveryCode()

        assertNotNull(code1)
        assertNotNull(code2)
        assertNotEquals(code1, code2)
        // Format: XXXX-XXXX-XXXX-XXXX (19 chars with dashes)
        assertEquals(19, code1.length)
        val normalized = cryptoManager.normalizeRecoveryCode(code1)
        assertEquals(16, normalized.length)
        assertTrue(normalized.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
    }

    @Test
    fun normalizeRecoveryCode_handlesDashesSpacesAndCase() {
        val raw = " ab34 - cd56 - EF78 - 9012 "
        val normalized = cryptoManager.normalizeRecoveryCode(raw)
        assertEquals("AB34CD56EF789012", normalized)
    }
}
