package com.pixelguard.applock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.pixelguard.applock.data.local.PreferencesDataStore
import com.pixelguard.applock.data.repository.AppLockRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppLockApplication : Application() {

    lateinit var repository: AppLockRepositoryImpl
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val cryptoManager: com.pixelguard.applock.data.local.CryptoManager by lazy {
        com.pixelguard.applock.data.local.CryptoManager()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val dataStore = PreferencesDataStore(applicationContext)
        repository = AppLockRepositoryImpl(applicationContext, dataStore)
        createNotificationChannels()

        applicationScope.launch {
            val attempts = dataStore.globalFailedAttemptsFlow.first()
            val wallClockExpiry = dataStore.globalLockoutWallClockTimestampFlow.first()
            val durationMs = dataStore.globalLockoutDurationMsFlow.first()
            val now = System.currentTimeMillis()
            if (wallClockExpiry in 1..now || (wallClockExpiry == 0L && durationMs > 0L)) {
                // The persisted lockout expired while the app was closed or device was off.
                // Clear the expired lockout from DataStore so it does not phantom-lock on launch.
                dataStore.saveGlobalLockout(attempts, 0L, 0L)
                com.pixelguard.applock.domain.usecase.SessionManager.syncPersistedLockout(
                    attempts = attempts,
                    wallClockExpiryMs = 0L,
                    durationRemainingMs = 0L
                )
            } else {
                com.pixelguard.applock.domain.usecase.SessionManager.syncPersistedLockout(
                    attempts = attempts,
                    wallClockExpiryMs = wallClockExpiry,
                    durationRemainingMs = durationMs
                )
            }
        }
        com.pixelguard.applock.domain.usecase.SessionManager.onLockoutChanged = { attempts, durationMs, wallClockExpiryMs ->
            applicationScope.launch {
                dataStore.saveGlobalLockout(attempts, durationMs, wallClockExpiryMs)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val protectionChannel = NotificationChannel(
                CHANNEL_ID_PROTECTION,
                getString(R.string.foreground_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification to maintain continuous background protection"
                setShowBadge(false)
            }

            val maskedChannel = NotificationChannel(
                CHANNEL_ID_MASKED,
                "Notification Privacy Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Masks sensitive notification content for locked applications"
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(protectionChannel)
            notificationManager.createNotificationChannel(maskedChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_PROTECTION = "pixel_app_lock_protection_channel"
        const val CHANNEL_ID_MASKED = "pixel_app_lock_masked_channel"
        lateinit var instance: AppLockApplication
            private set
    }
}
