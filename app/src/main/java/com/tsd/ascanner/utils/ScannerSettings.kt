package com.tsd.ascanner.utils

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ScannerSettings {
    private const val PREFS_NAME = "scanner_settings"
    private const val KEY_KEYBOARD_MODE = "keyboard_mode_enabled"

    var keyboardModeEnabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        keyboardModeEnabled = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEYBOARD_MODE, false)
    }

    fun setKeyboardMode(context: Context, enabled: Boolean) {
        keyboardModeEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEYBOARD_MODE, enabled)
            .apply()
    }
}
