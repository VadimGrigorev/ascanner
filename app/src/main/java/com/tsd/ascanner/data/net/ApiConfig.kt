package com.tsd.ascanner.data.net

import com.tsd.ascanner.BuildConfig
import com.tsd.ascanner.AScannerApp

object ApiConfig {
    val baseUrl: String
		get() {
			val raw = ServerSettings.getBaseUrl(AScannerApp.instance).trim()
			return if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
				raw
			} else {
				"http://$raw"
			}
		}
}


