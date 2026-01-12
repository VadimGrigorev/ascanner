package com.tsd.ascanner.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-memory debug switches (not persisted). Resets on app process restart.
 */
object DebugSession {
	var debugModeEnabled by mutableStateOf(false)
}

