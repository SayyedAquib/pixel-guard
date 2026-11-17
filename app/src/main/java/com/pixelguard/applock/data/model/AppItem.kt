package com.pixelguard.applock.data.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
data class AppItem(
    val packageName: String,
    val appName: String,
    @Deprecated("Retained for binary compatibility; iconBitmap should be used instead")
    val icon: Drawable? = null,
    val iconBitmap: Bitmap? = null,
    val category: AppCategory = AppCategory.OTHER,
    val isLocked: Boolean = false,
    val customTags: List<String> = emptyList()
)
