package com.tsd.ascanner.data.net

import android.content.Context
import com.tsd.ascanner.BuildConfig

object ServerSettings {
	private const val PREFS_NAME = "server_settings"
	private const val KEY_BASE_URL = "server_base_url"

	@Volatile
	private var inMemoryBaseUrl: String? = null

	fun getBaseUrl(context: Context): String {
		inMemoryBaseUrl?.let { return it }
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val url = prefs.getString(KEY_BASE_URL, BuildConfig.API_BASE_URL)?.ifBlank { BuildConfig.API_BASE_URL }
			?: BuildConfig.API_BASE_URL
		inMemoryBaseUrl = url
		return url
	}

	fun setBaseUrl(context: Context, url: String) {
		val normalized = url.trim()
		inMemoryBaseUrl = normalized
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit()
			.putString(KEY_BASE_URL, normalized)
			.apply()
	}
}



