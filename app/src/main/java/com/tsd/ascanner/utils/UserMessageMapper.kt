package com.tsd.ascanner.utils

object UserMessageMapper {
	private const val FRIENDLY_NETWORK_ERROR = "Не удалось соединиться с сервером. Проверьте интернет соединение!"

	fun map(e: Throwable): String? {
		// Walk through causes to detect common network connectivity issues
		var t: Throwable? = e
		while (t != null) {
			when (t) {
				is java.net.UnknownHostException,
				is java.net.ConnectException,
				is java.net.SocketTimeoutException -> return FRIENDLY_NETWORK_ERROR
			}
			t = t.cause
		}
		// Heuristic for message-only cases
		val msg = e.message?.lowercase() ?: return null
		return if (msg.contains("unable to resolve host") ||
			msg.contains("failed to connect") ||
			msg.contains("failed to connect to") ||
			msg.contains("connection timed out") ||
			msg.contains("timeout")
		) FRIENDLY_NETWORK_ERROR else null
	}
}


