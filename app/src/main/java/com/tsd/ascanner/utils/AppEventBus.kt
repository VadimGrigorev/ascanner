package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class AppEvent {
	object RequireLogin : AppEvent()
}

object AppEventBus {
	private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 1)
	val events = _events.asSharedFlow()

	fun requireLogin() {
		_events.tryEmit(AppEvent.RequireLogin)
	}
}



