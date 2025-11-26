package com.tsd.ascanner.data.net

import com.tsd.ascanner.BuildConfig
import com.tsd.ascanner.AScannerApp

object ApiConfig {
    val baseUrl: String
		get() {
			val raw = ServerSettings.getBaseUrl(AScannerApp.instance).trim()
			if (raw.isEmpty()) return raw

			val hasHttp = raw.startsWith("http://", ignoreCase = true)
			val hasHttps = raw.startsWith("https://", ignoreCase = true)
			val noScheme = when {
				hasHttp -> raw.removePrefix("http://")
				hasHttps -> raw.removePrefix("https://")
				else -> raw
			}

			// If user entered only a number (e.g. "151"), treat as 192.168.1.151:80
			val hostPort = if (noScheme.all { it.isDigit() }) {
				"192.168.1.$noScheme:80"
			} else {
				noScheme
			}

			return when {
				hasHttps -> "https://$hostPort"
				hasHttp -> "http://$hostPort"
				hostPort.startsWith("http://", ignoreCase = true) ||
					hostPort.startsWith("https://", ignoreCase = true) -> hostPort
				else -> "http://$hostPort"
			}
		}
}


