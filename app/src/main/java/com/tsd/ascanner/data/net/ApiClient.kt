package com.tsd.ascanner.data.net

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.IllegalStateException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.AppEventBus
import com.tsd.ascanner.utils.DialogBus
import com.tsd.ascanner.utils.PrintBus
import com.tsd.ascanner.utils.ServerDialog
import com.tsd.ascanner.utils.ServerDialogButton
import com.tsd.ascanner.utils.ServerDialogShownException
import com.tsd.ascanner.utils.ServerPrintRequest

class ApiClient(
    private val gson: Gson = Gson()
) {
    private val prettyGson: Gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

	companion object {
		// Глобальный флаг: включить/выключить все HTTP-логи нашего клиента
		private const val DEBUG_LOGS = true
	}

    suspend fun <T> postAndParse(
		path: String,
		body: Any,
		responseClass: Class<T>,
		logRequest: Boolean = false
	): T =
        withContext(Dispatchers.IO) {
                val url = URL(composeUrl(path))
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                try {
                    val json = gson.toJson(body)
                    if (DEBUG_LOGS && logRequest) {
                        Log.d("ApiClient", buildString {
                            append("REQUEST POST ").append(url)
                            if (path.isNotEmpty()) append(" (path=").append(path).append(')')
                            append(" body=").append(formatForLog(json))
                        })
                    }
                    BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { w ->
                        w.write(json)
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { br ->
                        br.readText()
                    }
                    if (DEBUG_LOGS && logRequest) {
                        Log.d("ApiClient", buildString {
                            append("RESPONSE ").append(code).append(" from ").append(url)
                            append(" body=").append(formatForLog(responseText))
                        })
                    }
                    // Global server-side error detection
                    throwIfServerError(responseText, throwOnDialog = true)
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTP $code: $responseText")
                    }
                    // Be lenient to tolerate trailing commas or minor format issues in mock responses
                    val reader = com.google.gson.stream.JsonReader(java.io.StringReader(responseText)).apply {
                        isLenient = true
                    }
                    gson.fromJson(reader, responseClass)
                } finally {
                    connection.disconnect()
                }
        }

    suspend fun postForJsonElement(
		path: String,
		body: Any,
		logRequest: Boolean = false
	): com.google.gson.JsonElement =
        withContext(Dispatchers.IO) {
                val url = URL(composeUrl(path))
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                try {
                    val json = gson.toJson(body)
                    if (DEBUG_LOGS && logRequest) {
                        Log.d("ApiClient", buildString {
                            append("REQUEST POST ").append(url)
                            if (path.isNotEmpty()) append(" (path=").append(path).append(')')
                            append(" body=").append(formatForLog(json))
                        })
                    }
                    BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { w ->
                        w.write(json)
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { br ->
                        br.readText()
                    }
                    if (DEBUG_LOGS && logRequest) {
                        Log.d("ApiClient", buildString {
                            append("RESPONSE ").append(code).append(" from ").append(url)
                            append(" body=").append(formatForLog(responseText))
                        })
                    }
                    // Global server-side error detection
                    throwIfServerError(responseText, throwOnDialog = false)
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTP $code: $responseText")
                    }
                    gson.fromJson(responseText, com.google.gson.JsonElement::class.java)
                } finally {
                    connection.disconnect()
                }
        }

    private fun throwIfServerError(responseText: String, throwOnDialog: Boolean) {
        try {
            val element = gson.fromJson(responseText, com.google.gson.JsonElement::class.java)
            if (element != null && element.isJsonObject) {
                val obj = element.asJsonObject
                val mt = if (obj.has("MessageType")) obj.get("MessageType").asString else null
				val form = if (obj.has("Form")) obj.get("Form").asString else null
				if (mt != null && mt.equals("dialog", ignoreCase = true)) {
					val formId = if (obj.has("FormId")) obj.get("FormId").asString else ""
					val header = if (obj.has("DialogHeader")) obj.get("DialogHeader").asString else ""
					val text = if (obj.has("DialogText")) obj.get("DialogText").asString else ""
					val status = if (obj.has("Status")) obj.get("Status").asString else ""
					val statusColor = when {
						obj.has("StatusColor") -> obj.get("StatusColor").asString
						obj.has("statusColor") -> obj.get("statusColor").asString
						else -> null
					}
					val buttons = buildList {
						if (obj.has("Buttons") && obj.get("Buttons").isJsonArray) {
							for (el in obj.getAsJsonArray("Buttons")) {
								if (!el.isJsonObject) continue
								val b = el.asJsonObject
								val name = if (b.has("Name")) b.get("Name").asString else ""
								val id = if (b.has("Id")) b.get("Id").asString else ""
								if (id.isNotBlank()) add(ServerDialogButton(name = name.ifBlank { id }, id = id))
							}
						}
					}
					DialogBus.emit(
						ServerDialog(
							form = form ?: "",
							formId = formId,
							header = header,
							text = text,
							status = status,
							statusColor = statusColor,
							buttons = buttons
						)
					)
					if (throwOnDialog) throw ServerDialogShownException()
					return
				}
				// Handle print command from server
				if (mt != null && mt.equals("print", ignoreCase = true)) {
					val formId = if (obj.has("FormId")) obj.get("FormId").asString else ""
					val picture = if (obj.has("Picture")) obj.get("Picture").asString else ""
					val pictureType = if (obj.has("PictureType")) obj.get("PictureType").asString else "bmp"
					val paperWidth = if (obj.has("PaperWidth")) {
						obj.get("PaperWidth").asString.toFloatOrNull() ?: 50f
					} else 50f
					val paperHeight = if (obj.has("PaperHeight")) {
						obj.get("PaperHeight").asString.toFloatOrNull() ?: 30f
					} else 30f
					val copies = if (obj.has("PrintCopies")) {
						obj.get("PrintCopies").asString.toIntOrNull() ?: 1
					} else 1
					
					if (picture.isNotBlank()) {
						PrintBus.emit(
							ServerPrintRequest(
								form = form ?: "",
								formId = formId,
								pictureBase64 = picture,
								pictureType = pictureType,
								paperWidthMm = paperWidth,
								paperHeightMm = paperHeight,
								copies = copies
							)
						)
					}
					return
				}
                if (mt != null && mt.equals("error", ignoreCase = true)) {
                    val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
					// Emit to global error bus for top-of-screen banner
                    ErrorBus.emit(msg)
					// Navigate to login if server instructs "Form":"login"
					if (form != null && form.equals("login", ignoreCase = true)) {
						AppEventBus.requireLogin()
					}
                    throw IllegalStateException(msg)
                }
            }
        } catch (e: Exception) {
			if (e is ServerDialogShownException) throw e
            // Non-JSON or invalid JSON: ignore here, HTTP code handling covers non-JSON errors
        }
    }

    private fun composeUrl(path: String): String {
        val base = ApiConfig.baseUrl.trimEnd('/')
        // Real server expects all requests at root path without endpoints
        return "$base/"
    }

    // Retries disabled by design: each request uses a single attempt with 5s timeouts.

    private fun formatForLog(raw: String): String {
        return try {
            val masked = maskSensitive(raw)
            val element = gson.fromJson(masked, com.google.gson.JsonElement::class.java)
            val compact = prettyGson.toJson(element)
                .replace("\\s+".toRegex(), " ")
                .trim()
            val maxLen = 600
            if (compact.length <= maxLen) compact
            else compact.take(maxLen) + "... (len=${compact.length})"
        } catch (_: Exception) {
            val singleLine = raw.replace("\\s+".toRegex(), " ").trim()
            val maxLen = 600
            if (singleLine.length <= maxLen) singleLine
            else singleLine.take(maxLen) + "... (len=${singleLine.length})"
        }
    }

    private fun maskSensitive(raw: String): String {
        return try {
            val element = gson.fromJson(raw, com.google.gson.JsonElement::class.java)
            maskRecursive(element)
            gson.toJson(element)
        } catch (_: Exception) {
            raw
        }
    }

    private fun maskRecursive(element: com.google.gson.JsonElement) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for ((key, value) in obj.entrySet()) {
                if (key.equals("Bearer", ignoreCase = true) ||
                    key.equals("Password", ignoreCase = true) ||
                    key.equals("Token", ignoreCase = true)
                ) {
                    obj.addProperty(key, "****")
                } else {
                    maskRecursive(value)
                }
            }
        } else if (element.isJsonArray) {
            val arr = element.asJsonArray
            arr.forEach { maskRecursive(it) }
        }
    }
}


