package com.tsd.ascanner.ui.screens.select

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.ui.components.ServerActionButtons
import com.tsd.ascanner.ui.components.parseServerIconOrFallback
import com.tsd.ascanner.ui.theme.AppTheme
import com.tsd.ascanner.ui.theme.statusCardColor
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.ServerSelect
import kotlinx.coroutines.launch

@Composable
fun SelectScreen(
	paddingValues: PaddingValues,
	select: ServerSelect?,
	onClose: () -> Unit
) {
	val ctx = androidx.compose.ui.platform.LocalContext.current
	val app = ctx.applicationContext as AScannerApp
	val docsService = app.docsService
	val colors = AppTheme.colors
	val scope = rememberCoroutineScope()
	var sending by remember { mutableStateOf(false) }

	Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
		val payload = select
		if (payload == null) {
			CircularProgressIndicator(
				modifier = Modifier.align(Alignment.Center),
				color = Color(0xFF30323D)
			)
		} else {
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				item {
					Column(modifier = Modifier.padding(12.dp)) {
						val header = payload.headerText ?: ""
						val statusText = payload.statusText ?: ""
						Text(text = header, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
						if (statusText.isNotBlank()) {
							Text(text = statusText, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
						}
					}
				}

				items(payload.items) { it ->
					val bg = statusCardColor(
						colors = colors,
						status = it.status,
						statusColor = it.statusColor,
						fallbackStatus = payload.status,
						fallbackStatusColor = payload.statusColor
					)
					val iconSpec = it.icon
					val icon = if (!iconSpec.isNullOrBlank()) parseServerIconOrFallback(iconSpec) else null
					Card(
						modifier = Modifier
							.padding(horizontal = 12.dp, vertical = 6.dp)
							.then(
								if (!payload.selectedId.isNullOrBlank() && payload.selectedId == it.id) {
									Modifier.border(width = 2.dp, color = Color.Black, shape = MaterialTheme.shapes.medium)
								} else {
									Modifier
								}
							)
							.fillMaxWidth()
							.clickable(enabled = !sending) {
								scope.launch {
									try {
										sending = true
										when (val res = docsService.sendSelect(
											form = payload.form,
											formId = payload.formId,
											selectedId = it.id
										)) {
											is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
												// Next screen/state will be driven by server response.
											}
											is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
												// Dialog/select will be shown globally (DialogBus/SelectBus).
											}
											is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
												ErrorBus.emit(res.message)
											}
										}
									} finally {
										sending = false
									}
								}
							},
						colors = CardDefaults.cardColors(containerColor = bg)
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(12.dp)
						) {
							if (icon != null) {
								Icon(
									imageVector = icon,
									contentDescription = null,
									tint = colors.textPrimary
								)
							}
							Column(modifier = Modifier.weight(1f)) {
								Text(text = it.name, color = colors.textPrimary)
								val comment = it.comment
								if (!comment.isNullOrBlank()) {
									Text(
										text = comment,
										color = colors.textSecondary,
										modifier = Modifier.padding(top = 4.dp)
									)
								}
							}
						}
					}
				}
			}

			// Optional server buttons (same behavior as on other screens)
			val serverButtons = payload.buttons
			if (serverButtons.isNotEmpty()) {
				Column(
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(16.dp),
					horizontalAlignment = Alignment.End,
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					ServerActionButtons(
						buttons = serverButtons,
						enabled = !sending,
						onClick = { b ->
							scope.launch {
								try {
									sending = true
									when (val res = docsService.sendButton(
										form = payload.form,
										formId = payload.formId,
										buttonId = b.id,
										requestType = "button"
									)) {
										is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
										}
										is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
										}
										is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
											ErrorBus.emit(res.message)
										}
									}
								} finally {
									sending = false
								}
							}
						}
					)
				}
			}
		}

		if (sending) {
			Box(modifier = Modifier.fillMaxSize()) {
				CircularProgressIndicator(
					modifier = Modifier.align(Alignment.Center),
					color = Color(0xFF30323D)
				)
			}
		}
	}

	BackHandler { onClose() }
}
