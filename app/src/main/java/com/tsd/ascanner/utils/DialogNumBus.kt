package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global bus for server-driven numeric input dialogs (MessageType="dialognum").
 */
object DialogNumBus {
	private val _events = MutableSharedFlow<ServerDialogNum>(extraBufferCapacity = 1)
	val events = _events.asSharedFlow()

	fun emit(dialog: ServerDialogNum) {
		_events.tryEmit(dialog)
	}
}
