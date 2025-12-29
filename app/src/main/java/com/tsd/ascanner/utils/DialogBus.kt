package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global bus for server-driven dialogs (MessageType="dialog").
 */
object DialogBus {
	private val _events = MutableSharedFlow<ServerDialog>(extraBufferCapacity = 1)
	val events = _events.asSharedFlow()

	fun emit(dialog: ServerDialog) {
		_events.tryEmit(dialog)
	}
}

