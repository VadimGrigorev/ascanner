package com.tsd.ascanner.data.docs

import com.tsd.ascanner.data.auth.AuthService
import com.tsd.ascanner.data.net.ApiClient

class DocsService(
    private val apiClient: ApiClient,
    private val authService: AuthService
) {
    @Volatile
    var currentDoc: DocOneResponse? = null
    @Volatile
    var currentPos: PosResponse? = null

    suspend fun fetchDocs(): DocListResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocListRequest(bearer = bearer)
        return apiClient.postAndParse("/docs", req, DocListResponse::class.java)
    }

    suspend fun fetchDoc(formId: String): DocOneResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocOneRequest(bearer = bearer, formId = formId)
        return apiClient.postAndParse("/doc", req, DocOneResponse::class.java)
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
            val doc = fetchDoc(formId)
            currentDoc = doc
            ScanDocResult.Success(doc)
        } catch (e: Exception) {
            ScanDocResult.Error(e.message ?: "Ошибка")
        }
    }

    suspend fun scanDocList(text: String): ScanDocResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocScanRequest(bearer = bearer, text = text)
        val element = apiClient.postForJsonElement("/scanlist", req)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanDocResult.Error(msg)
        }
        val form = if (obj.has("Form")) obj.get("Form").asString else null
        return if (form != null && form.equals("doc", ignoreCase = true)) {
            val doc = com.google.gson.Gson().fromJson(obj, DocOneResponse::class.java)
            currentDoc = doc
            ScanDocResult.Success(doc)
        } else {
            val selectedId = if (obj.has("SelectedId")) obj.get("SelectedId").asString else null
            if (!selectedId.isNullOrBlank()) {
                val doc = fetchDoc(selectedId)
                currentDoc = doc
                ScanDocResult.Success(doc)
            } else {
                ScanDocResult.Error("Документ не найден")
            }
        }
    }

    suspend fun scanMark(formId: String, text: String): ScanDocResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocScan2Request(bearer = bearer, formId = formId, text = text)
        val element = apiClient.postForJsonElement("/scan", req)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanDocResult.Error(msg)
        }
        val doc = com.google.gson.Gson().fromJson(obj, DocOneResponse::class.java)
        currentDoc = doc
        return ScanDocResult.Success(doc)
    }

    suspend fun fetchPos(formId: String): PosResponse {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = PosRequest(bearer = bearer, formId = formId)
        return apiClient.postAndParse("/pos", req, PosResponse::class.java)
    }

    suspend fun scanPosMark(formId: String, text: String): ScanPosResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = DocScan2Request(bearer = bearer, form = "pos", formId = formId, text = text)
        val element = apiClient.postForJsonElement("/scanone", req)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanPosResult.Error(msg)
        }
        val pos = com.google.gson.Gson().fromJson(obj, PosResponse::class.java)
        currentPos = pos
        return ScanPosResult.Success(pos)
    }

    suspend fun deletePosAll(formId: String): ScanPosResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = PosDeleteRequest(bearer = bearer, formId = formId, deleteId = "")
        val element = apiClient.postForJsonElement("/posdelete", req)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanPosResult.Error(msg)
        }
        val pos = com.google.gson.Gson().fromJson(obj, PosResponse::class.java)
        currentPos = pos
        return ScanPosResult.Success(pos)
    }

    suspend fun deletePosItem(formId: String, deleteId: String): ScanPosResult {
        val bearer = authService.bearer ?: throw IllegalStateException("Нет токена авторизации")
        val req = PosDeleteRequest(bearer = bearer, formId = formId, deleteId = deleteId)
        val element = apiClient.postForJsonElement("/posdelete", req)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
            return ScanPosResult.Error(msg)
        }
        val pos = com.google.gson.Gson().fromJson(obj, PosResponse::class.java)
        currentPos = pos
        return ScanPosResult.Success(pos)
    }

    fun clear() {
        currentDoc = null
        currentPos = null
    }
}

sealed interface ScanDocResult {
    data class Success(val doc: DocOneResponse) : ScanDocResult
    data class Error(val message: String) : ScanDocResult
}

sealed interface ScanPosResult {
    data class Success(val pos: PosResponse) : ScanPosResult
    data class Error(val message: String) : ScanPosResult
}


