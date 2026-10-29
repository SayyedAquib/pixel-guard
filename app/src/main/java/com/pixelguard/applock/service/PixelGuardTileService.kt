package com.pixelguard.applock.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.pixelguard.applock.AppLockApplication
import com.pixelguard.applock.domain.usecase.SessionManager
import com.pixelguard.applock.ui.lock.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class PixelGuardTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        scope.launch {
            val prefs = AppLockApplication.instance.repository.preferences.first()
            if (!prefs.isQuickTileEnabled) {
                // If tile toggle is disabled in settings, prompt user or keep disabled
                val tile = qsTile ?: return@launch
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Pixel Guard"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Disabled in Settings"
                }
                tile.updateTile()
                return@launch
            }

            if (SessionManager.isPaused()) {
                SessionManager.resumeProtection()
                updateTileState()
            } else {
                // Securely require Biometric/PIN authentication before pausing protection
                val intent = Intent(this@PixelGuardTileService, LockActivity::class.java).apply {
                    action = LockActivity.ACTION_PAUSE_REQUEST
                    putExtra(LockActivity.EXTRA_PAUSE_MINUTES, prefs.quickTilePauseMinutes)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this@PixelGuardTileService,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        scope.launch {
            val prefs = AppLockApplication.instance.repository.preferences.first()
            if (!prefs.isQuickTileEnabled) {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Pixel Guard"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Disabled in Settings"
                }
                tile.updateTile()
                return@launch
            }

            if (SessionManager.isPaused()) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Pixel Guard"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val remainingMinutes = SessionManager.getRemainingPauseMinutes()
                    tile.subtitle = if (remainingMinutes < 0) "Paused (Until Resumed)" else "Paused (${remainingMinutes}m)"
                }
            } else {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Pixel Guard"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Protection Active"
                }
            }
            tile.updateTile()
        }
    }
}
