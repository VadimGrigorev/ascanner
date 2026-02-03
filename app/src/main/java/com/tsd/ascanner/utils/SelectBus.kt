package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global bus for server-driven select pages (MessageType="select").
 */
object SelectBus {
	private val _events = MutableSharedFlow<ServerSelect>(extraBufferCapacity = 1)
	val events = _events.asSharedFlow()

	fun emit(select: ServerSelect) {
		_events.tryEmit(select)
	}
}

