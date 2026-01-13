package com.tsd.ascanner.data.docs

import com.tsd.ascanner.data.auth.AuthService
import com.tsd.ascanner.data.net.ApiClient
import com.google.gson.Gson
import com.tsd.ascanner.utils.ServerDialogShownException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DocsService(
    private val apiClient: ApiClient,
    private val authService: AuthService
) {
	private val _currentDoc = MutableStateFlow<DocOneResponse?>(null)
	val currentDocFlow = _currentDoc.asStateFlow()

	/**
	 * Backward-compatible imperative accessors.
	 * Prefer [currentDocFlow] in UI for reactive updates.
	 */
	var currentDoc: DocOneResponse?
		get() = _currentDoc.value
		set(value) {
			_currentDoc.value = value
		}

	private val _currentPos = MutableStateFlow<PosResponse?>(null)
	val currentPosFlow = _currentPos.asStateFlow()

	/**
	 * Backward-compatible imperative accessors.
	 * Prefer [currentPosFlow] in UI for reactive updates.
	 */
	var currentPos: PosResponse?
		get() = _currentPos.value
		set(value) {
			_currentPos.value = value
		}

    suspend fun fetchDocs(logRequest: Boolean): DocListResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocListRequest(bearer = bearer)
        return apiClient.postAndParse("/docs", req, DocListResponse::class.java, logRequest = logRequest)
    }

    suspend fun fetchDoc(formId: String, logRequest: Boolean): DocOneResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocOneRequest(bearer = bearer, formId = formId)
        return apiClient.postAndParse("/doc", req, DocOneResponse::class.java, logRequest = logRequest)
    }

    private fun extractFormIdFromScanned(scannedText: String): String? {
        // Проверяем только наличие маркера, возвращаем исходную строку без изменений
        val marker = "NEWDOCUMENT"
        if (!scannedText.contains(marker)) return null
        return scannedText
    }

    suspend fun fetchDocFromScan(scannedText: String): ScanDocResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val formId = extractFormIdFromScanned(scannedText)
            ?: return ScanDocResult.Error("неверный формат штрихкода")
        return try {
            val doc = fetchDoc(formId, logRequest = true)
            currentDoc = doc
            ScanDocResult.Success(doc)
        } catch (_: ServerDialogShownException) {
            // Dialog will be shown via global DialogBus
            ScanDocResult.DialogShown
        } catch (e: Exception) {
            ScanDocResult.Error(e.message ?: "Ошибка")
        }
    }

    suspend fun scanDocList(text: String): ScanDocResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocScanRequest(bearer = bearer, text = text)
        val element = apiClient.postForJsonElement("/scanlist", req, logRequest = true)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			return ScanDocResult.DialogShown
		}
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanDocResult.Error(msg)
        }
        val form = if (obj.has("Form")) obj.get("Form").asString else null
        return if (form != null && form.equals("doc", ignoreCase = true)) {
            val doc = Gson().fromJson(obj, DocOneResponse::class.java)
            currentDoc = doc
            ScanDocResult.Success(doc)
        } else {
            val selectedId = if (obj.has("SelectedId")) obj.get("SelectedId").asString else null
            if (!selectedId.isNullOrBlank()) {
                try {
                    val doc = fetchDoc(selectedId, logRequest = true)
                    currentDoc = doc
                    ScanDocResult.Success(doc)
                } catch (_: ServerDialogShownException) {
                    // Dialog will be shown via global DialogBus
                    ScanDocResult.DialogShown
                }
            } else {
                ScanDocResult.Error("Документ не найден")
            }
        }
    }

    suspend fun scanMark(formId: String, text: String): ScanDocResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocScan2Request(bearer = bearer, formId = formId, text = text)
        val element = apiClient.postForJsonElement("/scan", req, logRequest = true)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			return ScanDocResult.DialogShown
		}
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanDocResult.Error(msg)
        }
        val doc = Gson().fromJson(obj, DocOneResponse::class.java)
        currentDoc = doc
        return ScanDocResult.Success(doc)
    }

    suspend fun fetchPos(formId: String, logRequest: Boolean): PosResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = PosRequest(bearer = bearer, formId = formId)
        return apiClient.postAndParse("/pos", req, PosResponse::class.java, logRequest = logRequest)
    }

    suspend fun scanPosMark(formId: String, text: String): ScanPosResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocScan2Request(bearer = bearer, form = "pos", formId = formId, text = text)
        val element = apiClient.postForJsonElement("/scanone", req, logRequest = true)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			return ScanPosResult.DialogShown
		}
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanPosResult.Error(msg)
        }
        val pos = Gson().fromJson(obj, PosResponse::class.java)
        currentPos = pos
        return ScanPosResult.Success(pos)
    }

    suspend fun deletePosAll(formId: String): ScanPosResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = PosDeleteRequest(bearer = bearer, formId = formId, deleteId = "")
        val element = apiClient.postForJsonElement("/posdelete", req, logRequest = true)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			return ScanPosResult.DialogShown
		}
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanPosResult.Error(msg)
        }
        val pos = Gson().fromJson(obj, PosResponse::class.java)
        currentPos = pos
        return ScanPosResult.Success(pos)
    }

    suspend fun deletePosItem(formId: String, deleteId: String): ScanPosResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = PosDeleteRequest(bearer = bearer, formId = formId, deleteId = deleteId)
        val element = apiClient.postForJsonElement("/posdelete", req, logRequest = true)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			return ScanPosResult.DialogShown
		}
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanPosResult.Error(msg)
        }
        val pos = Gson().fromJson(obj, PosResponse::class.java)
        currentPos = pos
        return ScanPosResult.Success(pos)
    }

	suspend fun sendButton(form: String, formId: String, buttonId: String, requestType: String = "dialog"): ButtonResult {
		val bearer = authService.bearer ?: return ButtonResult.Error("Нет токена авторизации")
		val req = ButtonRequest(bearer = bearer, form = form, formId = formId, request = requestType, buttonId = buttonId)
		return try {
			val element = apiClient.postForJsonElement("/button", req, logRequest = true)
			if (!element.isJsonObject) return ButtonResult.Success
			val obj = element.asJsonObject
			val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
			if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
				return ButtonResult.DialogShown
			}
			if (messageType != null && messageType.equals("error", ignoreCase = true)) {
				val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
				return ButtonResult.Error(msg)
			}
			val respForm = if (obj.has("Form")) obj.get("Form").asString else form
			when (respForm.lowercase()) {
				"doc" -> {
					val doc = Gson().fromJson(obj, DocOneResponse::class.java)
					currentDoc = doc
				}
				"pos" -> {
					val pos = Gson().fromJson(obj, PosResponse::class.java)
					currentPos = pos
				}
			}
			ButtonResult.Success
		} catch (e: Exception) {
			ButtonResult.Error(e.message ?: "Ошибка запроса")
		}
	}

    fun clear() {
        currentDoc = null
        currentPos = null
    }
}

sealed interface ScanDocResult {
    data class Success(val doc: DocOneResponse) : ScanDocResult
    data class Error(val message: String) : ScanDocResult
	data object DialogShown : ScanDocResult
}

sealed interface ScanPosResult {
    data class Success(val pos: PosResponse) : ScanPosResult
    data class Error(val message: String) : ScanPosResult
	data object DialogShown : ScanPosResult
}

sealed interface ButtonResult {
	data object Success : ButtonResult
	data class Error(val message: String) : ButtonResult
	data object DialogShown : ButtonResult
}


