package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global bus to surface server error messages to UI (top-of-screen banner).
 */
object ErrorBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun emit(message: String) {
        _events.tryEmit(message)
    }
}


