package com.tsd.ascanner.data.docs

import com.tsd.ascanner.data.auth.AuthService
import com.tsd.ascanner.data.net.ApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tsd.ascanner.utils.ServerDialogShownException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

	private val _navEvents = MutableSharedFlow<NavTarget>(extraBufferCapacity = 16)
	val navEvents = _navEvents.asSharedFlow()

	private suspend fun routeByForm(
		obj: JsonObject,
		fallbackForm: String? = null,
		emitNav: Boolean = true
	) {
		// Server-side print is a side-effect, not a screen/form update.
		// If we treat it as a normal "doc"/"pos" response, we may overwrite UI state
		// with an empty model (print payload doesn't contain HeaderText/Items/etc),
		// which looks like a white/blank screen until the next refresh.
		val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("print", ignoreCase = true)) {
			return
		}

		val respForm = when {
			obj.has("Form") -> obj.get("Form").asString
			!fallbackForm.isNullOrBlank() -> fallbackForm
			else -> ""
		}.trim()

		val formId = when {
			obj.has("FormId") -> obj.get("FormId").asString
			else -> null
		}

		when (respForm.lowercase()) {
			"doc" -> {
				val doc = Gson().fromJson(obj, DocOneResponse::class.java)
				currentDoc = doc
				if (emitNav) {
					_navEvents.emit(NavTarget(form = "doc", formId = doc.formId ?: formId))
				}
			}
			"pos" -> {
				val pos = Gson().fromJson(obj, PosResponse::class.java)
				currentPos = pos
				if (emitNav) {
					_navEvents.emit(NavTarget(form = "pos", formId = pos.formId ?: formId))
				}
			}
		}
	}

    suspend fun fetchDocs(logRequest: Boolean): DocListResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocListRequest(bearer = bearer)
        return apiClient.postAndParse("/docs", req, DocListResponse::class.java, logRequest = logRequest)
    }

    suspend fun fetchDoc(formId: String, logRequest: Boolean, emitNav: Boolean = true): DocOneResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocOneRequest(bearer = bearer, formId = formId)
		val element = apiClient.postForJsonElement("/doc", req, logRequest = logRequest)
		val obj = element.asJsonObject
		val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			// Dialog will be shown via global DialogBus
			throw ServerDialogShownException()
		}
		routeByForm(obj, fallbackForm = "doc", emitNav = emitNav)
		// Backward compatible return type: return a minimal instance if server returned a different Form.
		return if (obj.has("Form") && obj.get("Form").asString.equals("doc", ignoreCase = true)) {
			Gson().fromJson(obj, DocOneResponse::class.java)
		} else {
			DocOneResponse(formId = formId)
		}
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
            // currentDoc/currentPos are already updated inside fetchDoc() via routeByForm()
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
		// Print is a side-effect handled globally (PrintBus + PrinterDialog). It is not a document payload.
		if (messageType != null && messageType.equals("print", ignoreCase = true)) {
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
                    // currentDoc/currentPos are already updated inside fetchDoc() via routeByForm()
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
		routeByForm(obj, fallbackForm = "doc")
		// Close scan overlay even if server returned different Form (navigation is handled globally)
		val returnedDoc = if (obj.has("Form") && obj.get("Form").asString.equals("doc", ignoreCase = true)) {
			Gson().fromJson(obj, DocOneResponse::class.java)
		} else {
			DocOneResponse(formId = formId)
		}
        return ScanDocResult.Success(returnedDoc)
    }

    suspend fun fetchPos(formId: String, logRequest: Boolean, emitNav: Boolean = true): PosResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = PosRequest(bearer = bearer, formId = formId)
		val element = apiClient.postForJsonElement("/pos", req, logRequest = logRequest)
		val obj = element.asJsonObject
		val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			// Dialog will be shown via global DialogBus
			throw ServerDialogShownException()
		}
		routeByForm(obj, fallbackForm = "pos", emitNav = emitNav)
		// Backward compatible return type: return a minimal instance if server returned a different Form.
		return if (obj.has("Form") && obj.get("Form").asString.equals("pos", ignoreCase = true)) {
			Gson().fromJson(obj, PosResponse::class.java)
		} else {
			PosResponse(formId = formId)
		}
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
		routeByForm(obj, fallbackForm = "pos")
		val returnedPos = if (obj.has("Form") && obj.get("Form").asString.equals("pos", ignoreCase = true)) {
			Gson().fromJson(obj, PosResponse::class.java)
		} else {
			PosResponse(formId = formId)
		}
        return ScanPosResult.Success(returnedPos)
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
		routeByForm(obj, fallbackForm = "pos")
		val returnedPos = if (obj.has("Form") && obj.get("Form").asString.equals("pos", ignoreCase = true)) {
			Gson().fromJson(obj, PosResponse::class.java)
		} else {
			PosResponse(formId = formId)
		}
        return ScanPosResult.Success(returnedPos)
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
		routeByForm(obj, fallbackForm = "pos")
		val returnedPos = if (obj.has("Form") && obj.get("Form").asString.equals("pos", ignoreCase = true)) {
			Gson().fromJson(obj, PosResponse::class.java)
		} else {
			PosResponse(formId = formId)
		}
        return ScanPosResult.Success(returnedPos)
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
			routeByForm(obj, fallbackForm = form)
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

data class NavTarget(
	val form: String,
	val formId: String? = null
)


