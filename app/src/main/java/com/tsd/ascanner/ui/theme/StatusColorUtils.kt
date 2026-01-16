package com.tsd.ascanner.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Server may provide hex colors with or without leading '#'.
 * Supported formats: RRGGBB or AARRGGBB.
 */
fun parseHexColorOrNull(raw: String?): Color? {
	val s = raw?.trim()?.removePrefix("#") ?: return null
	if (s.isBlank()) return null
	if (s.length != 6 && s.length != 8) return null
	val normalized = "#$s"
	return try {
		Color(android.graphics.Color.parseColor(normalized))
	} catch (_: IllegalArgumentException) {
		null
	}
}

/**
 * Choose card/background color for a status.
 * - If [statusColor] is present and valid, it wins.
 * - Otherwise uses known status mapping.
 * - If status is unknown/blank, optional fallback can be used (e.g. pos-level defaults).
 */
fun statusCardColor(
	colors: AppColors,
	status: String?,
	statusColor: String?,
	fallbackStatus: String? = null,
	fallbackStatusColor: String? = null
): Color {
	parseHexColorOrNull(statusColor)?.let { return it }

	fun mapStatus(s: String?): Color? = when ((s ?: "").trim().lowercase()) {
		"closed" -> colors.statusDoneBg
		"pending" -> colors.statusPendingBg
		"note" -> colors.statusNoteBg
		"warning" -> colors.statusWarningBg
		"error" -> colors.statusErrorBg
		// "open" and anything else -> null (handled by fallback / default)
		else -> null
	}

	mapStatus(status)?.let { return it }
	parseHexColorOrNull(fallbackStatusColor)?.let { return it }
	return mapStatus(fallbackStatus) ?: colors.statusTodoBg
}

