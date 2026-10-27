package com.pixelguard.applock.data.repository

import com.pixelguard.applock.data.local.CryptoManager
import com.pixelguard.applock.data.local.PreferencesDataStore
import com.pixelguard.applock.domain.usecase.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockRepositoryTest {

    private lateinit var dataStore: PreferencesDataStore
    private lateinit var cryptoManager: CryptoManager
    private lateinit var repository: AppLockRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        dataStore = PreferencesDataStore(context)
        cryptoManager = CryptoManager()
        repository = AppLockRepositoryImpl(context, dataStore, cryptoManager)
        SessionManager.resetFailedAttempts(context.packageName)
    }

    @After
    fun tearDown() {
        repository.cancelScope()
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPin_withWeakPin_throwsIllegalArgumentException() = runTest {
        repository.setPin("123456")
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPin_withRepeatingDigits_throwsIllegalArgumentException() = runTest {
        repository.setPin("111111")
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPin_withNon6Digits_throwsIllegalArgumentException() = runTest {
        repository.setPin("1234")
    }

    @Test
    fun setPin_withStrong6DigitPin_savesPinAndReturnsRecoveryCode() = runTest {
        val recoveryCode = repository.setPin("849201")
        assertNotNull(recoveryCode)
        assertEquals(19, recoveryCode.length)
        assertTrue(repository.hasPinSet())
    }

    @Test
    fun verifyPinResult_withCorrectPin_returnsSuccess() = runTest {
        repository.setPin("849201")
        val result = repository.verifyPinResult("849201")
        assertTrue(result is VerifyPinResult.Success)
        assertTrue(repository.verifyPin("849201"))
    }

    @Test
    fun verifyPinResult_withWrongPin_returnsWrongPinAndDecrementsRemaining() = runTest {
        repository.setPin("849201")
        val result = repository.verifyPinResult("849202")
        assertTrue(result is VerifyPinResult.WrongPin)
        assertFalse(repository.verifyPin("849202"))
    }

    @Test
    fun verifyRecoveryCode_andReset_successfullyClearsPin() = runTest {
        val recoveryCode = repository.setPin("849201")
        assertTrue(repository.hasPinSet())

        val isValid = repository.verifyRecoveryCode(recoveryCode)
        assertTrue("Recovery code must be valid", isValid)

        val resetSuccess = repository.resetWithRecoveryCode(recoveryCode)
        assertTrue("Reset with recovery code must succeed", resetSuccess)
        assertFalse("PIN must be cleared after reset", repository.hasPinSet())
    }

    @Test
    fun resetWithRecoveryCode_withInvalidCode_returnsFalse() = runTest {
        repository.setPin("849201")
        val resetSuccess = repository.resetWithRecoveryCode("INVALID-CODE-1234")
        assertFalse(resetSuccess)
        assertTrue(repository.hasPinSet())
    }

    @Test
    fun verifyPinResult_whenEnvelopeCorrupted_returnsUnrecoverable() = runTest {
        repository.setPin("849201")
        // Tamper with dataStore directly to simulate Keystore loss or corrupted ciphertext envelope
        dataStore.savePinWithRecovery(
            pinHash = "corrupted_non_envelope_payload",
            pinSalt = "corrupted_salt",
            recoveryHash = "corrupted_rec",
            recoverySalt = "corrupted_salt"
        )
        val result = repository.verifyPinResult("849201")
        assertEquals(VerifyPinResult.Unrecoverable, result)
    }
}

