package com.pixelguard.applock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pixelguard.applock.data.local.PreferencesDataStore
import com.pixelguard.applock.domain.usecase.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_USER_UNLOCKED
        ) {
            // 1. Wipe all temporary unlock sessions immediately on device reboot/package update
            SessionManager.clearAllSessions()

            // 2. Start AppLockForegroundService immediately on the main thread (0ms delay)
            AppLockForegroundService.start(context)

            // 3. PG-SEC-12: Use goAsync() for background lockout synchronization
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dataStore = PreferencesDataStore(context)
                    val attempts = dataStore.globalFailedAttemptsFlow.first()
                    val wallClockExpiry = dataStore.globalLockoutWallClockTimestampFlow.first()
                    val durationMs = dataStore.globalLockoutDurationMsFlow.first()
                    SessionManager.syncPersistedLockout(
                        attempts = attempts,
                        wallClockExpiryMs = wallClockExpiry,
                        durationRemainingMs = durationMs
                    )
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
