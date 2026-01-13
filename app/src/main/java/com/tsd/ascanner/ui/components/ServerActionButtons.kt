package com.tsd.ascanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
			val icon = parseIconOrFallback(b.icon)
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

private fun parseIconOrFallback(iconSpec: String?): ImageVector {
	// Expecting: "icons.{outlined|filled|rounded}.{name}", e.g. "icons.outlined.add"
	val styleName = iconSpec?.substringBeforeLast('.')?.substringAfterLast('.')?.lowercase()
	val rawName = iconSpec?.substringAfterLast('.')?.lowercase()
	return when (styleName) {
		"filled" -> mapFilled(rawName)
		"rounded" -> mapRounded(rawName)
		else -> mapOutlined(rawName) // default to outlined
	}
}

private fun mapOutlined(name: String?): ImageVector {
	return when (name) {
		"add" -> Icons.Outlined.Add
		"delete" -> Icons.Outlined.Delete
		"delete_forever" -> Icons.Outlined.Delete
		"refresh" -> Icons.Outlined.Refresh
		"photo_camera" -> Icons.Outlined.PhotoCamera
		"print" -> Icons.Outlined.Print
		else -> Icons.Outlined.Add
	}
}

private fun mapFilled(name: String?): ImageVector {
	return when (name) {
		"add" -> Icons.Filled.Add
		"delete" -> Icons.Filled.Delete
		"delete_forever" -> Icons.Filled.Delete
		"refresh" -> Icons.Filled.Refresh
		"photo_camera" -> Icons.Filled.PhotoCamera
		"print" -> Icons.Filled.Print
		else -> Icons.Filled.Add
	}
}

private fun mapRounded(name: String?): ImageVector {
	return when (name) {
		"add" -> Icons.Rounded.Add
		"delete" -> Icons.Rounded.Delete
		"delete_forever" -> Icons.Rounded.Delete
		"refresh" -> Icons.Rounded.Refresh
		"photo_camera" -> Icons.Rounded.PhotoCamera
		"print" -> Icons.Rounded.Print
		else -> Icons.Rounded.Add
	}
}

