package com.tsd.ascanner.ui.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

sealed interface SearchScanMode {
	data class Marker(val marker: String) : SearchScanMode
	data object ControlChars : SearchScanMode
}

@Composable
fun ServerSearchField(
	visible: Boolean,
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	scanMode: SearchScanMode,
	onScan: (String) -> Unit,
	modifier: Modifier = Modifier,
	onFocusChanged: ((Boolean) -> Unit)? = null
) {
	if (!visible) return

	fun commitScan(code: String) {
		val trimmed = code.trim()
		if (trimmed.isBlank()) return
		onValueChange("")
		onScan(trimmed)
	}

	OutlinedTextField(
		value = value,
		onValueChange = { newValue ->
			when (scanMode) {
				is SearchScanMode.Marker -> {
					onValueChange(newValue)
					val idx = newValue.indexOf(scanMode.marker)
					if (idx >= 0) {
						// Keep backward-compatible behavior: send marker+tail as scanned payload
						commitScan(newValue.substring(idx))
					}
				}
				SearchScanMode.ControlChars -> {
					val idx = newValue.indexOfFirst { ch ->
						val code = ch.code
						((code in 0x00..0x1F) && code != 0x1D) || code == 0x7F
					}
					if (idx >= 0) {
						commitScan(newValue.substring(0, idx))
					} else {
						// Manual input should only filter and must NOT trigger scan.
						onValueChange(newValue)
					}
				}
			}
		},
		modifier = modifier.onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
		label = { Text(text = label) },
		singleLine = true
	)
}

