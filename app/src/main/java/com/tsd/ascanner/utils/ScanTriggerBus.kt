package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Simple bus to propagate hardware trigger press/release to composables.
 */
object ScanTriggerBus {
    private val _events = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun emitPressed() { _events.tryEmit(true) }
    fun emitReleased() { _events.tryEmit(false) }
}


