package com.tsd.ascanner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.concurrent.ConcurrentHashMap

/**
 * Parses server icon spec into a Material icon.
 *
 * Expected format: `icons.{outlined|filled|rounded}.{name}`, e.g. `icons.outlined.print`.
 * Unknown icons fall back to `add`.
 */
fun parseServerIconOrFallback(iconSpec: String?): ImageVector {
	val styleName = iconSpec?.substringBeforeLast('.')?.substringAfterLast('.')?.lowercase()
	val rawName = iconSpec?.substringAfterLast('.')?.lowercase()?.trim()
	val normalizedName = normalizeIconName(rawName)

	val pack = when (styleName) {
		"filled" -> "filled"
		"rounded" -> "rounded"
		else -> "outlined" // default to outlined (also covers explicit "outlined")
	}
	val group: Any = when (pack) {
		"filled" -> Icons.Filled
		"rounded" -> Icons.Rounded
		else -> Icons.Outlined
	}

	// Try to resolve dynamically from material-icons-extended (e.g. Icons.Outlined.Person).
	resolveMaterialIconOrNull(pack, group, normalizedName)?.let { return it }

	// Known aliases that sometimes appear in server payloads.
	when (normalizedName) {
		"plus" -> resolveMaterialIconOrNull(pack, group, "add")?.let { return it }
		"minus" -> resolveMaterialIconOrNull(pack, group, "remove")?.let { return it }
	}

	return Icons.Outlined.Add
}

private fun normalizeIconName(raw: String?): String? {
	val s = raw?.trim()?.lowercase().orEmpty()
	if (s.isBlank()) return null
	return s.replace('-', '_')
}

private val iconCache = ConcurrentHashMap<String, ImageVector>()
private val iconMisses = ConcurrentHashMap<String, Boolean>()

private fun resolveMaterialIconOrNull(pack: String, group: Any, rawName: String?): ImageVector? {
	if (rawName.isNullOrBlank()) return null
	val pascal = toPascalCase(rawName)
	if (pascal.isBlank()) return null

	val cacheKey = "$pack|$pascal"
	iconCache[cacheKey]?.let { return it }
	if (iconMisses.containsKey(cacheKey)) return null

	// Compose material icons are exposed as extension properties compiled into
	// a per-icon class, e.g. androidx.compose.material.icons.outlined.PersonKt#getPerson(Icons.Outlined).
	val clsName = "androidx.compose.material.icons.$pack.${pascal}Kt"
	val getterName = "get$pascal"
	return try {
		val cls = Class.forName(clsName)
		val m = cls.methods.firstOrNull { it.name == getterName && it.parameterCount == 1 }
			?: run {
				iconMisses[cacheKey] = true
				return null
			}
		val v = (m.invoke(null, group) as? ImageVector)
		if (v != null) iconCache[cacheKey] = v else iconMisses[cacheKey] = true
		v
	} catch (_: Throwable) {
		iconMisses[cacheKey] = true
		null
	}
}

private fun toPascalCase(raw: String): String {
	return raw
		.split('_')
		.filter { it.isNotBlank() }
		.joinToString("") { part ->
			val p = part.lowercase()
			p.replaceFirstChar { ch -> ch.uppercaseChar() }
		}
}

