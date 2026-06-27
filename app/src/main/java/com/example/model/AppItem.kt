package com.example.model

import android.graphics.Bitmap

data class InstalledAppItem(
    val packageName: String,
    val appLabel: String,
    val icon: Bitmap, // Ultra-fast rendering
    val isSystem: Boolean
)
