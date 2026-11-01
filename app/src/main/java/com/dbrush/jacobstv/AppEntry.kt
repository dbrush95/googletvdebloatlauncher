package com.dbrush.jacobstv

import android.content.ComponentName
import android.graphics.drawable.Drawable

enum class TileKind { APP, SETTINGS, ABOUT }

data class AppEntry(
    val label: String,
    val kind: TileKind,
    val icon: Drawable?,
    val component: ComponentName?,
)
