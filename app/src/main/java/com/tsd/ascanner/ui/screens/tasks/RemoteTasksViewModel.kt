package com.tsd.ascanner.ui.screens.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsd.ascanner.data.docs.DocsService
import com.tsd.ascanner.data.docs.TaskDto
import com.tsd.ascanner.data.docs.ActionButtonDto
import com.tsd.ascanner.ui.theme.parseHexColorOrNull
import com.tsd.ascanner.utils.ServerDialogShownException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RemoteTasksViewModel(
    private val docsService: DocsService
) : ViewModel() {
    var tasks by mutableStateOf<List<TaskDto>>(emptyList())
        private set
	var buttons by mutableStateOf<List<ActionButtonDto>>(emptyList())
		private set
	var backgroundColorHex by mutableStateOf<String?>(null)
		private set
	var isSearchAvailable by mutableStateOf(false)
		private set
    var expandedTaskIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var showOnlyOpen by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
		var searchQuery by mutableStateOf("")
			private set
		var showLoadingIndicator by mutableStateOf(true)
			private set

		// Internal flag to avoid overlapping background refreshes
		private var isAutoRefreshing: Boolean = false

		init {
			viewModelScope.launch {
				docsService.currentDocListFlow
					.filterNotNull()
					.collectLatest { resp ->
						tasks = resp.tasks
						buttons = resp.buttons
						val available = resp.searchAvailable?.equals("true", ignoreCase = true) == true
						isSearchAvailable = available
						if (!available && searchQuery.isNotBlank()) {
							searchQuery = ""
						}
						if (parseHexColorOrNull(resp.backgroundColor) != null) {
							backgroundColorHex = resp.backgroundColor
						}
					}
			}
		}

	fun refresh(userInitiated: Boolean = true) {
		if (userInitiated) {
			// Block if manual load already in progress
			if (isLoading) return
			showLoadingIndicator = true
			isLoading = true
			errorMessage = null
			viewModelScope.launch {
				try {
					val resp = docsService.fetchDocs(logRequest = true)
					tasks = resp.tasks
					buttons = resp.buttons
					val available = resp.searchAvailable?.equals("true", ignoreCase = true) == true
					isSearchAvailable = available
					if (!available && searchQuery.isNotBlank()) {
						searchQuery = ""
					}
					if (parseHexColorOrNull(resp.backgroundColor) != null) {
						backgroundColorHex = resp.backgroundColor
					}
				} catch (e: Exception) {
					if (e !is ServerDialogShownException) {
						errorMessage = e.message ?: "Ошибка загрузки документов"
					}
				} finally {
					isLoading = false
					showLoadingIndicator = true
				}
			}
		} else {
			// Background auto-refresh: не показываем индикатор и не блокируем клики,
			// но не даём наслаиваться запросам и не мешаем ручной загрузке
			if (isLoading || isAutoRefreshing) return
			isAutoRefreshing = true
			viewModelScope.launch {
				try {
					val resp = docsService.fetchDocs(logRequest = false)
					tasks = resp.tasks
					buttons = resp.buttons
					val available = resp.searchAvailable?.equals("true", ignoreCase = true) == true
					isSearchAvailable = available
					if (!available && searchQuery.isNotBlank()) {
						searchQuery = ""
					}
					if (parseHexColorOrNull(resp.backgroundColor) != null) {
						backgroundColorHex = resp.backgroundColor
					}
				} catch (e: Exception) {
					// Можно при желании не трогать errorMessage, чтобы не спамить баннером
					if (e !is ServerDialogShownException) {
						errorMessage = e.message ?: "Ошибка загрузки документов"
					}
				} finally {
					isAutoRefreshing = false
				}
			}
		}
	}

    fun toggleTask(taskId: String) {
        expandedTaskIds = if (expandedTaskIds.contains(taskId)) {
            expandedTaskIds - taskId
        } else {
            expandedTaskIds + taskId
        }
    }

    fun toggleShowOnlyOpen() {
        showOnlyOpen = !showOnlyOpen
    }

	fun openOrder(orderId: String, onSuccessOpen: () -> Unit) {
        if (isLoading) return
		showLoadingIndicator = true
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                // Server decides which Form to show; navigation happens via DocsService.navEvents in MainActivity.
                docsService.fetchDoc(orderId, logRequest = true)
            } catch (e: Exception) {
				if (e !is ServerDialogShownException) {
					errorMessage = e.message ?: "Ошибка открытия документа"
				}
            } finally {
                isLoading = false
            }
        }
    }

	fun updateSearchQuery(value: String) {
		searchQuery = value
	}
}


