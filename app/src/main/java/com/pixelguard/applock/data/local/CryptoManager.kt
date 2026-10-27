package com.pixelguard.applock.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class CryptoManager {

    private val keyLock = Any()

    @androidx.annotation.VisibleForTesting
    var testSecretKey: SecretKey? = null

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        testSecretKey?.let { return it }
        val ks = keyStore
        if (ks == null) {
            val fallback = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            testSecretKey = fallback
            return fallback
        }
        val existingKey = try {
            ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        } catch (_: Throwable) {
            null
        }
        return existingKey?.secretKey ?: try {
            createKey()
        } catch (_: Throwable) {
            val fallback = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            testSecretKey = fallback
            fallback
        }
    }

    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        // Titan M2 / StrongBox Hardware Security Module (SEC-007):
        // Attempt dedicated StrongBox hardware-isolated key generation; gracefully fallback to TEE
        return try {
            val strongBoxSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .setIsStrongBoxBacked(true)
                .build()
            keyGenerator.init(strongBoxSpec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            // Standard TEE fallback when StrongBox is unavailable (e.g. emulators)
            val teeSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(teeSpec)
            keyGenerator.generateKey()
        }
    }

    /**
     * Hashes a PIN using PBKDF2WithHmacSHA256 with 200,000 iterations.
     * Memory Hygiene (SEC-006 & PG-SEC-13): Zeroes out PBEKeySpec, caller's CharArray buffer,
     * and intermediate derived key byte arrays.
     */
    fun hashPin(pin: CharArray, salt: ByteArray): String {
        val pinCopy = pin.clone()
        val spec = PBEKeySpec(
            pinCopy,
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword() // Zero out the PBEKeySpec internal buffer
        pinCopy.fill('0')
        pin.fill('0') // Zero out caller's char buffer
        val hexString = byteArrayToHex(hash)
        hash.fill(0) // Zero out intermediate derived key bytes from memory (PG-SEC-13)
        return hexString
    }

    /**
     * Converts a byte array to a lowercase hex string using [java.util.Locale.ROOT]
     * to prevent non-ASCII digit conversions on Turkish, Arabic, or Persian locales (H-2).
     */
    fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }

    /**
     * Parses a hex string strictly into a [ByteArray] using [java.util.Locale.ROOT].
     * Throws [IllegalArgumentException] on odd length, unexpected byte count, or non-hex characters (M-2).
     */
    fun hexToByteArray(hex: String, expectedByteCount: Int? = null): ByteArray {
        if (hex.length % 2 != 0) {
            throw IllegalArgumentException("Hex string must have an even length, got length ${hex.length}")
        }
        if (expectedByteCount != null && hex.length != expectedByteCount * 2) {
            throw IllegalArgumentException("Expected ${expectedByteCount * 2} hex chars (${expectedByteCount} bytes), got ${hex.length}")
        }
        val result = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            val high = Character.digit(hex[i], 16)
            val low = Character.digit(hex[i + 1], 16)
            if (high == -1 || low == -1) {
                throw IllegalArgumentException("Non-hex character encountered in hex string: '$hex'")
            }
            result[i / 2] = ((high shl 4) or low).toByte()
        }
        return result
    }

    fun isWeakPin(pin: String): Boolean = Companion.isWeakPin(pin)

    fun generateRecoveryCode(): String = Companion.generateRecoveryCode()

    fun normalizeRecoveryCode(raw: String): String = Companion.normalizeRecoveryCode(raw)

    fun hashPin(pin: String, salt: ByteArray): String {
        val chars = pin.toCharArray()
        return hashPin(chars, salt)
    }

    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)
        return salt
    }

    fun verifyHash(computedHash: String, expectedHash: String): Boolean {
        return MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun encryptData(data: ByteArray, aad: ByteArray = ByteArray(0)): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return Pair(encrypted, iv)
    }

    fun decryptData(encryptedData: ByteArray, iv: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(encryptedData)
    }

    /**
     * Encrypts arbitrary binary data (e.g. intruder selfie JPEG) into a self-contained
     * container format: [1 byte IV length][IV bytes][AES-GCM Ciphertext + Tag].
     */
    fun encryptToFileBytes(data: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val (encrypted, iv) = encryptData(data, aad)
        val buffer = java.nio.ByteBuffer.allocate(1 + iv.size + encrypted.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(encrypted)
        return buffer.array()
    }

    /**
     * Decrypts a container format produced by [encryptToFileBytes].
     * PG-SEC-07: Validates buffer length and IV boundaries to eliminate BufferUnderflowException.
     */
    fun decryptFromFileBytes(fileBytes: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        // Minimal valid AES-GCM envelope: 1 byte (ivLen) + 12 bytes (IV) + 16 bytes (auth tag) = 29 bytes
        if (fileBytes.size < 1 + 12 + 16) {
            throw IllegalArgumentException("Truncated or corrupted ciphertext container: size=${fileBytes.size}")
        }
        val buffer = java.nio.ByteBuffer.wrap(fileBytes)
        val ivLength = buffer.get().toInt() and 0xFF
        if (ivLength != 12 || buffer.remaining() < ivLength + 16) {
            throw IllegalArgumentException("Invalid IV length or insufficient ciphertext payload: ivLength=$ivLength, remaining=${buffer.remaining()}")
        }
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        return decryptData(encrypted, iv, aad)
    }

    /**
     * Encrypts a string (e.g. pin_hash, pin_salt) using the Titan M2 Keystore key
     * and returns a base64-encoded encrypted container with format prefix "enc:v1:".
     * Bounds encryption to [aad] (Associated Authenticated Data) preventing ciphertext swapping (M-10).
     */
    fun encryptString(plaintext: String, aad: String = ""): String {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        val aadBytes = if (aad.isNotEmpty()) aad.toByteArray(Charsets.UTF_8) else ByteArray(0)
        val encContainer = encryptToFileBytes(bytes, aadBytes)
        return "enc:v1:" + base64Encode(encContainer)
    }

    /**
     * Decrypts a string produced by [encryptString].
     * Enforces authenticated envelope validation and AAD matching; rejects unauthenticated plaintext (M-10).
     */
    fun decryptString(storedValue: String, aad: String = ""): String {
        val isV1 = storedValue.startsWith("enc:v1:")
        val isLegacyEnc = storedValue.startsWith("enc:")
        if (!isV1 && !isLegacyEnc) {
            throw SecurityException("Unauthenticated storage envelope: missing encrypted prefix")
        }
        val base64Payload = if (isV1) storedValue.removePrefix("enc:v1:") else storedValue.removePrefix("enc:")
        val encBytes = base64Decode(base64Payload)
        val aadBytes = if (isV1 && aad.isNotEmpty()) aad.toByteArray(Charsets.UTF_8) else ByteArray(0)
        val decBytes = decryptFromFileBytes(encBytes, aadBytes)
        return String(decBytes, Charsets.UTF_8)
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        // NOTE: Do NOT rename KEY_ALIAS — changing it destroys existing users' stored keys.
        private const val KEY_ALIAS = "pixel_app_lock_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ITERATIONS = 200_000
        private const val PBKDF2_KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
        private const val RECOVERY_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        /**
         * Detects weak or trivial PIN combinations (H-1).
         * Enforces exactly 6 numeric digits and blocks trivial/sequential/repeating patterns.
         */
        fun isWeakPin(pin: String): Boolean {
            if (pin.length != 6 || !pin.all { it in '0'..'9' }) {
                return true
            }
            // 1. All identical digits: "000000", "111111", etc.
            if (pin.toSet().size == 1) return true

            // 2. Sequential ascending or descending: "123456", "654321", "234567"
            val isAscending = pin.zipWithNext().all { (a, b) -> b.code - a.code == 1 }
            val isDescending = pin.zipWithNext().all { (a, b) -> a.code - b.code == 1 }
            if (isAscending || isDescending) return true

            // 3. Repeated 2-digit patterns: "121212", "505050"
            if (pin.substring(0, 2).repeat(3) == pin) return true

            // 4. Repeated 3-digit patterns: "123123"
            if (pin.substring(0, 3).repeat(2) == pin) return true

            // 5. Common weak blocklist
            val blocklist = setOf(
                "123456", "654321", "111111", "000000", "123123", "121212",
                "112233", "789456", "159753", "147258", "987654", "012345"
            )
            return blocklist.contains(pin)
        }

        /**
         * Generates a 16-character alphanumeric recovery key grouped in 4 blocks (H-3):
         * e.g. "ABCD-EFGH-JKLM-NPQR" using an unambiguous character set (no 0, 1, I, O).
         */
        fun generateRecoveryCode(): String {
            val random = java.security.SecureRandom()
            val sb = StringBuilder(19)
            for (i in 0 until 16) {
                if (i > 0 && i % 4 == 0) sb.append('-')
                val index = random.nextInt(RECOVERY_CHARS.length)
                sb.append(RECOVERY_CHARS[index])
            }
            return sb.toString()
        }

        /**
         * Normalizes a recovery code by stripping dashes, spaces, and converting to uppercase (H-3).
         */
        fun normalizeRecoveryCode(raw: String): String {
            return raw.replace("-", "")
                .replace(" ", "")
                .uppercase(java.util.Locale.ROOT)
        }
    }
}
