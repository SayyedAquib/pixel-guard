package com.pixelguard.applock.data.local

import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DiagnosticExporter(
    private val cryptoManager: CryptoManager = CryptoManager()
) {

    fun generateAuditPayload(
        appVersion: String,
        versionCode: Int,
        deviceModel: String,
        androidVersion: Int,
        securityPatch: String,
        isAccessibilityActive: Boolean,
        isNotificationShieldActive: Boolean,
        snapshot: SecuritySnapshot?,
        intruderRecordsCount: Int,
        globalFailedAttempts: Int,
        isLockedOut: Boolean,
        remainingLockoutSeconds: Int
    ): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportTimestamp", System.currentTimeMillis())
        root.put("appVersion", appVersion)
        root.put("versionCode", versionCode)

        val deviceObj = JSONObject().apply {
            put("model", deviceModel)
            put("androidVersion", androidVersion)
            put("securityPatch", securityPatch)
        }
        root.put("device", deviceObj)

        val securityObj = JSONObject().apply {
            put("isAccessibilityActive", isAccessibilityActive)
            put("isNotificationShieldActive", isNotificationShieldActive)
            put("isSettingsProtected", snapshot?.isSettingsProtected ?: false)
            put("isPlayStoreProtected", snapshot?.isPlayStoreProtected ?: false)
            put("isScheduleEnabled", snapshot?.isScheduleEnabled ?: false)
            put("scheduleStartHour", snapshot?.scheduleStartHour ?: 23)
            put("scheduleEndHour", snapshot?.scheduleEndHour ?: 7)
            put("gracePeriodSeconds", snapshot?.gracePeriodSeconds ?: 0)
            put("isPerAppGraceEnabled", snapshot?.isPerAppGraceEnabled ?: false)
            put("lockedAppsCount", snapshot?.lockedPackages?.size ?: 0)
            put("intruderRecordsCount", intruderRecordsCount)
            put("globalFailedAttempts", globalFailedAttempts)
            put("isLockedOut", isLockedOut)
            put("remainingLockoutSeconds", remainingLockoutSeconds)
        }
        root.put("security", securityObj)

        return root.toString(2)
    }

    fun encryptAuditPayload(payload: String, recoveryKey: String): ByteArray {
        val normKey = cryptoManager.normalizeRecoveryCode(recoveryKey)
        val salt = ByteArray(SALT_BYTES).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(GCM_IV_BYTES).apply { SecureRandom().nextBytes(this) }

        val key = deriveAesKey(normKey.toCharArray(), salt)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        // Container format: [1 byte magic/version][16 bytes salt][12 bytes IV][ciphertext + tag]
        val buffer = ByteBuffer.allocate(1 + salt.size + iv.size + ciphertext.size)
        buffer.put(CONTAINER_VERSION)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    fun decryptAuditPayload(container: ByteArray, recoveryKey: String): String {
        val minLength = 1 + SALT_BYTES + GCM_IV_BYTES + (GCM_TAG_LENGTH_BITS / 8)
        if (container.size < minLength) {
            throw IllegalArgumentException("Malformed or truncated diagnostic container")
        }

        val buffer = ByteBuffer.wrap(container)
        val version = buffer.get()
        if (version != CONTAINER_VERSION) {
            throw IllegalArgumentException("Unsupported diagnostic container version: $version")
        }

        val salt = ByteArray(SALT_BYTES)
        buffer.get(salt)

        val iv = ByteArray(GCM_IV_BYTES)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val normKey = cryptoManager.normalizeRecoveryCode(recoveryKey)
        val key = deriveAesKey(normKey.toCharArray(), salt)

        return try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            val plaintextBytes = cipher.doFinal(ciphertext)
            String(plaintextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw SecurityException("Authentication failure: recovery key incorrect or payload corrupted", e)
        }
    }

    private fun deriveAesKey(passcode: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passcode, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        passcode.fill('0')
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        const val FORMAT_VERSION = "1.0"
        private const val CONTAINER_VERSION: Byte = 0x01
        private const val SALT_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PBKDF2_ITERATIONS = 20_000
        private const val KEY_LENGTH_BITS = 256
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    }
}

