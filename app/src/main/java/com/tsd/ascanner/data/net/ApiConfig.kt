package com.tsd.ascanner.data.net

import com.tsd.ascanner.BuildConfig
import com.tsd.ascanner.AScannerApp

object ApiConfig {
    val baseUrl: String
		get() = ServerSettings.getBaseUrl(AScannerApp.instance)
}


