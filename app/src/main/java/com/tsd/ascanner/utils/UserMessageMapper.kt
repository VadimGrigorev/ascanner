package com.tsd.ascanner.utils

object UserMessageMapper {
	private const val FRIENDLY_NETWORK_ERROR = "Не удалось соединиться с сервером. Проверьте интернет соединение!"
	private const val TIMEOUT_ERROR = "Таймаут ожидания ответа от сервера"

	fun map(e: Throwable): String? {
		// Walk through causes to detect common network connectivity issues
		var t: Throwable? = e
		while (t != null) {
			when (t) {
				is java.net.SocketTimeoutException -> return TIMEOUT_ERROR
				is java.net.UnknownHostException,
				is java.net.ConnectException -> return FRIENDLY_NETWORK_ERROR
			}
			t = t.cause
		}
		// Heuristic for message-only cases
		val msg = e.message?.lowercase() ?: return null
		return when {
			msg.contains("timeout") || msg.contains("timed out") || msg.contains("connection timed out") -> TIMEOUT_ERROR
			msg.contains("unable to resolve host") ||
				msg.contains("failed to connect") ||
				msg.contains("failed to connect to") -> FRIENDLY_NETWORK_ERROR
			else -> null
		}
	}
}


