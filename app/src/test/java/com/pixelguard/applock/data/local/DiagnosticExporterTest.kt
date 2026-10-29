package com.pixelguard.applock.data.local

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class DiagnosticExporterTest {

    private val cryptoManager = CryptoManager()
    private val exporter = DiagnosticExporter(cryptoManager)

    @Test
    fun `generateAuditPayload contains required sanitized fields and zero private data`() {
        val snapshot = SecuritySnapshot(
            lockedPackages = setOf("com.example.bank", "com.example.social"),
            isSettingsProtected = true,
            isPlayStoreProtected = false,
            isScheduleEnabled = true,
            scheduleStartHour = 22,
            scheduleEndHour = 6,
            gracePeriodSeconds = 30,
            isPerAppGraceEnabled = true,
            appGracePeriodsJson = "{\"com.example.bank\":15}"
        )

        val jsonStr = exporter.generateAuditPayload(
            appVersion = "1.0.0",
            versionCode = 1,
            deviceModel = "Pixel 9",
            androidVersion = 35,
            securityPatch = "2026-10-01",
            isAccessibilityActive = true,
            isNotificationShieldActive = true,
            snapshot = snapshot,
            intruderRecordsCount = 3,
            globalFailedAttempts = 2,
            isLockedOut = false,
            remainingLockoutSeconds = 0
        )

        val json = JSONObject(jsonStr)

        // 1. Metadata verification
        assertThat(json.getString("formatVersion")).isEqualTo("1.0")
        assertThat(json.getString("appVersion")).isEqualTo("1.0.0")
        assertThat(json.getInt("versionCode")).isEqualTo(1)

        // 2. Device info verification
        val deviceObj = json.getJSONObject("device")
        assertThat(deviceObj.getString("model")).isEqualTo("Pixel 9")
        assertThat(deviceObj.getInt("androidVersion")).isEqualTo(35)
        assertThat(deviceObj.getString("securityPatch")).isEqualTo("2026-10-01")

        // 3. Security posture & shields
        val securityObj = json.getJSONObject("security")
        assertThat(securityObj.getBoolean("isAccessibilityActive")).isTrue()
        assertThat(securityObj.getBoolean("isNotificationShieldActive")).isTrue()
        assertThat(securityObj.getBoolean("isSettingsProtected")).isTrue()
        assertThat(securityObj.getBoolean("isPlayStoreProtected")).isFalse()
        assertThat(securityObj.getInt("lockedAppsCount")).isEqualTo(2)
        assertThat(securityObj.getInt("intruderRecordsCount")).isEqualTo(3)

        // 4. Privacy Guarantee: Zero package names or personal details in export
        assertThat(jsonStr).doesNotContain("com.example.bank")
        assertThat(jsonStr).doesNotContain("com.example.social")
        assertThat(jsonStr).doesNotContain("pin")
        assertThat(jsonStr).doesNotContain("salt")
        assertThat(jsonStr).doesNotContain("hash")
    }

    @Test
    fun `exportEncryptedAudit encrypts payload and can be decrypted with recovery key`() {
        val rawPayload = "{\"testKey\":\"sanitizedValue\"}"
        val recoveryKey = "ABCD-EFGH-JKLM-NPQR"

        val encryptedContainer = exporter.encryptAuditPayload(rawPayload, recoveryKey)

        assertThat(encryptedContainer).isNotEmpty()
        assertThat(encryptedContainer).isNotEqualTo(rawPayload.toByteArray(Charsets.UTF_8))

        // Decrypt using the same recovery key
        val decryptedStr = exporter.decryptAuditPayload(encryptedContainer, recoveryKey)
        assertThat(decryptedStr).isEqualTo(rawPayload)
    }

    @Test(expected = SecurityException::class)
    fun `decryptAuditPayload throws SecurityException on wrong recovery key`() {
        val rawPayload = "{\"testKey\":\"sanitizedValue\"}"
        val validKey = "ABCD-EFGH-JKLM-NPQR"
        val wrongKey = "ZZZZ-YYYY-XXXX-WWWW"

        val encryptedContainer = exporter.encryptAuditPayload(rawPayload, validKey)
        exporter.decryptAuditPayload(encryptedContainer, wrongKey)
    }
}

