package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global bus for server-driven print requests (MessageType="print").
 * 
 * When the server sends a print command, ApiClient parses it and emits
 * a ServerPrintRequest through this bus. MainActivity listens to this
 * and triggers the print dialog/process.
 */
object PrintBus {
    private val _events = MutableSharedFlow<ServerPrintRequest>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun emit(request: ServerPrintRequest) {
        _events.tryEmit(request)
    }
}
