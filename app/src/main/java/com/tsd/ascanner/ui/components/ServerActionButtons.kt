package com.tsd.ascanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tsd.ascanner.data.docs.ActionButtonDto
import com.tsd.ascanner.ui.theme.AppTheme

@Composable
fun ServerActionButtons(
	buttons: List<ActionButtonDto>,
	enabled: Boolean = true,
	onClick: (ActionButtonDto) -> Unit
) {
	if (buttons.isEmpty()) return
	val colors = AppTheme.colors
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		buttons.forEach { b ->
			val container = b.color?.let { parseHexColorOrNull(it) } ?: colors.secondary
			val icon = parseServerIconOrFallback(b.icon)
			FloatingActionButton(
				onClick = { if (enabled) onClick(b) },
				containerColor = container,
				contentColor = colors.textPrimary
			) {
				Icon(imageVector = icon, contentDescription = (b.name ?: "").ifBlank { b.id })
			}
		}
	}
}

private fun parseHexColorOrNull(hex: String): Color? {
	val s = hex.trim().removePrefix("#")
	return try {
		when (s.length) {
			6 -> {
				val rgb = s.toInt(16)
				val argb = (0xFF shl 24) or rgb
				Color(argb)
			}
			8 -> {
				val argb = s.toLong(16).toInt()
				Color(argb)
			}
			else -> null
		}
	} catch (_: Exception) {
		null
	}
}

