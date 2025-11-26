package com.tsd.ascanner.ui.screens.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsd.ascanner.data.docs.DocsService
import com.tsd.ascanner.data.docs.TaskDto
import kotlinx.coroutines.launch

class RemoteTasksViewModel(
    private val docsService: DocsService
) : ViewModel() {
    var tasks by mutableStateOf<List<TaskDto>>(emptyList())
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

	fun refresh(userInitiated: Boolean = true) {
		if (userInitiated) {
			// Block if manual load already in progress
			if (isLoading) return
			showLoadingIndicator = true
			isLoading = true
			errorMessage = null
			viewModelScope.launch {
				try {
					val resp = docsService.fetchDocs()
					tasks = resp.tasks
				} catch (e: Exception) {
					errorMessage = e.message ?: "Ошибка загрузки документов"
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
					val resp = docsService.fetchDocs()
					tasks = resp.tasks
				} catch (e: Exception) {
					// Можно при желании не трогать errorMessage, чтобы не спамить баннером
					errorMessage = e.message ?: "Ошибка загрузки документов"
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
                val doc = docsService.fetchDoc(orderId)
                docsService.currentDoc = doc
                onSuccessOpen()
            } catch (e: Exception) {
                errorMessage = e.message ?: "Ошибка открытия документа"
            } finally {
                isLoading = false
            }
        }
    }

	fun updateSearchQuery(value: String) {
		searchQuery = value
	}
}


