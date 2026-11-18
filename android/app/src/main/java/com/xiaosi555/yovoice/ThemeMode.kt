package com.xiaosi555.yovoice

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

/**
 * One app-wide DayNight policy shared by every Activity.
 * MODE_NIGHT_AUTO on UiModeManager clears the app-specific override so Android
 * inherits the device's current night configuration; it does not change the
 * device-wide night mode.
 */
object ThemeMode {
    fun apply(context: Context, appearance: String) {
        val appCompatMode = when (appearance) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val platformMode = when (appearance) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            val platformApplied = runCatching {
                val manager = context.getSystemService(UiModeManager::class.java)
                if (manager == null) false else {
                    manager.setApplicationNightMode(platformMode)
                    true
                }
            }.getOrDefault(false)
            // Android 12+ owns the app-local uiMode (including splash screen); use
            // AppCompat only as a fallback if the platform API is unavailable.
            if (!platformApplied) AppCompatDelegate.setDefaultNightMode(appCompatMode)
        } else {
            AppCompatDelegate.setDefaultNightMode(appCompatMode)
        }
    }
}
