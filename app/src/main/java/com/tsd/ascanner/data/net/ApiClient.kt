package com.tsd.ascanner.data.net

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.AppEventBus
import com.tsd.ascanner.utils.DialogBus
import com.tsd.ascanner.utils.PrintBus
import com.tsd.ascanner.utils.SelectBus
import com.tsd.ascanner.utils.ServerDialog
import com.tsd.ascanner.utils.ServerDialogButton
import com.tsd.ascanner.utils.ServerDialogNum
import com.tsd.ascanner.utils.DialogNumBus
import com.tsd.ascanner.utils.ServerDialogShownException
import com.tsd.ascanner.utils.ServerErrorResponseException
import com.tsd.ascanner.utils.ServerPrintRequest
import com.tsd.ascanner.utils.ServerSelect
import com.tsd.ascanner.utils.ServerSelectItem
import com.tsd.ascanner.data.docs.ActionButtonDto
import okhttp3.Call
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class ApiClient(
    private val gson: Gson = Gson()
) {
    private val prettyGson: Gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

	companion object {
		private const val DEBUG_LOGS = true
	}

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .apply {
            if (DEBUG_LOGS) eventListenerFactory { DebugEventListener() }
        }
        .build()

    suspend fun <T> postAndParse(
		path: String,
		body: Any,
		responseClass: Class<T>,
		logRequest: Boolean = false
	): T =
        withContext(Dispatchers.IO) {
            val url = composeUrl(path)
            val json = gson.toJson(body)
            if (DEBUG_LOGS && logRequest) {
                Log.d("ApiClient", buildString {
                    append("REQUEST POST ").append(url)
                    if (path.isNotEmpty()) append(" (path=").append(path).append(')')
                    append(" body=").append(formatForLog(json))
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string() ?: ""
                val code = response.code
                if (DEBUG_LOGS && logRequest) {
                    Log.d("ApiClient", buildString {
                        append("RESPONSE ").append(code).append(" from ").append(url)
                        append(" body=").append(formatForLog(responseText))
                    })
                }
                throwIfServerError(responseText, throwOnDialog = true)
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code: $responseText")
                }
                val reader = com.google.gson.stream.JsonReader(java.io.StringReader(responseText)).apply {
                    isLenient = true
                }
                gson.fromJson(reader, responseClass)
            }
        }

    suspend fun postForJsonElement(
		path: String,
		body: Any,
		logRequest: Boolean = false
	): com.google.gson.JsonElement =
        withContext(Dispatchers.IO) {
            val url = composeUrl(path)
            val json = gson.toJson(body)
            if (DEBUG_LOGS && logRequest) {
                Log.d("ApiClient", buildString {
                    append("REQUEST POST ").append(url)
                    if (path.isNotEmpty()) append(" (path=").append(path).append(')')
                    append(" body=").append(formatForLog(json))
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string() ?: ""
                val code = response.code
                if (DEBUG_LOGS && logRequest) {
                    Log.d("ApiClient", buildString {
                        append("RESPONSE ").append(code).append(" from ").append(url)
                        append(" body=").append(formatForLog(responseText))
                    })
                }
                throwIfServerError(responseText, throwOnDialog = false)
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code: $responseText")
                }
                gson.fromJson(responseText, com.google.gson.JsonElement::class.java)
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
					val backgroundColor = when {
						obj.has("BackgroundColor") -> obj.get("BackgroundColor").asString
						obj.has("backgroundColor") -> obj.get("backgroundColor").asString
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
							backgroundColor = backgroundColor,
							buttons = buttons
						)
					)
					if (throwOnDialog) throw ServerDialogShownException()
					return
				}
				if (mt != null && mt.equals("dialognum", ignoreCase = true)) {
					val formId = if (obj.has("FormId")) obj.get("FormId").asString else ""
					val header = if (obj.has("DialogHeader")) obj.get("DialogHeader").asString else ""
					val text = if (obj.has("DialogText")) obj.get("DialogText").asString else ""
					val status = if (obj.has("Status")) obj.get("Status").asString else ""
					val numberLength = if (obj.has("NumberLength")) {
						obj.get("NumberLength").asString.toIntOrNull() ?: 15
					} else 15
					val numberScale = if (obj.has("NumberScale")) {
						obj.get("NumberScale").asString.toIntOrNull() ?: 3
					} else 3
					val numberId = if (obj.has("NumberId")) obj.get("NumberId").asString else ""
					DialogNumBus.emit(
						ServerDialogNum(
							form = form ?: "",
							formId = formId,
							header = header,
							text = text,
							status = status,
							numberLength = numberLength,
							numberScale = numberScale,
							numberId = numberId
						)
					)
					if (throwOnDialog) throw ServerDialogShownException()
					return
				}
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
					val selectOnExit = if (obj.has("SelectOnExit")) obj.get("SelectOnExit").asString else ""
					
					if (picture.isNotBlank()) {
						PrintBus.emit(
							ServerPrintRequest(
								form = form ?: "",
								formId = formId,
								pictureBase64 = picture,
								pictureType = pictureType,
								paperWidthMm = paperWidth,
								paperHeightMm = paperHeight,
								copies = copies,
								selectOnExit = selectOnExit
							)
						)
					}
					return
				}
				if (mt != null && mt.equals("select", ignoreCase = true)) {
					val formId = if (obj.has("FormId")) obj.get("FormId").asString else ""
					val headerText = if (obj.has("HeaderText")) obj.get("HeaderText").asString else ""
					val statusText = if (obj.has("StatusText")) obj.get("StatusText").asString else ""
					val status = if (obj.has("Status")) obj.get("Status").asString else ""
					val statusColor = when {
						obj.has("StatusColor") -> obj.get("StatusColor").asString
						obj.has("statusColor") -> obj.get("statusColor").asString
						else -> null
					}
					val backgroundColor = when {
						obj.has("BackgroundColor") -> obj.get("BackgroundColor").asString
						obj.has("backgroundColor") -> obj.get("backgroundColor").asString
						else -> null
					}
					val searchAvailable = if (obj.has("SearchAvailable")) obj.get("SearchAvailable").asString else null
					val selectedId = if (obj.has("SelectedId")) obj.get("SelectedId").asString else null
					val items = buildList {
						if (obj.has("Items") && obj.get("Items").isJsonArray) {
							for (el in obj.getAsJsonArray("Items")) {
								if (!el.isJsonObject) continue
								val it = el.asJsonObject
								val name = if (it.has("Name")) it.get("Name").asString else ""
								val id = if (it.has("Id")) it.get("Id").asString else ""
								if (id.isBlank()) continue
								val comment = if (it.has("Comment")) it.get("Comment").asString else null
								val itemStatus = if (it.has("Status")) it.get("Status").asString else null
								val itemStatusColor = when {
									it.has("StatusColor") -> it.get("StatusColor").asString
									it.has("statusColor") -> it.get("statusColor").asString
									else -> null
								}
								val icon = if (it.has("Icon")) it.get("Icon").asString else null
								add(
									ServerSelectItem(
										name = name,
										id = id,
										comment = comment,
										status = itemStatus,
										statusColor = itemStatusColor,
										icon = icon
									)
								)
							}
						}
					}
					val buttons = buildList {
						if (obj.has("Buttons") && obj.get("Buttons").isJsonArray) {
							for (el in obj.getAsJsonArray("Buttons")) {
								if (!el.isJsonObject) continue
								val b = el.asJsonObject
								val id = if (b.has("Id")) b.get("Id").asString else ""
								if (id.isBlank()) continue
								val name = if (b.has("Name")) b.get("Name").asString else null
								val icon = if (b.has("Icon")) b.get("Icon").asString else null
								val color = if (b.has("Color")) b.get("Color").asString else null
								add(ActionButtonDto(id = id, name = name, icon = icon, color = color))
							}
						}
					}
					SelectBus.emit(
						ServerSelect(
							form = form ?: "",
							formId = formId,
							selectedId = selectedId,
							headerText = headerText,
							statusText = statusText,
							status = status,
							statusColor = statusColor,
							backgroundColor = backgroundColor,
							searchAvailable = searchAvailable,
							items = items,
							buttons = buttons
						)
					)
					if (throwOnDialog) throw ServerDialogShownException()
					return
				}
                if (mt != null && mt.equals("error", ignoreCase = true)) {
                    val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
                    val selectedId = if (obj.has("SelectedId")) obj.get("SelectedId").asString else null
                    ErrorBus.emit(msg)
					if (form != null && form.equals("login", ignoreCase = true)) {
						AppEventBus.requireLogin()
					}
                    throw ServerErrorResponseException(message = msg, selectedId = selectedId)
                }
            }
        } catch (e: Exception) {
			if (e is ServerDialogShownException) throw e
            if (e is IllegalStateException) throw e
        }
    }

    private fun composeUrl(path: String): String {
        val base = ApiConfig.baseUrl.trimEnd('/')
        return "$base/"
    }

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

    private class DebugEventListener : EventListener() {
        private var callStartNanos = 0L

        override fun callStart(call: Call) {
            callStartNanos = System.nanoTime()
            Log.d("OkHttp", "[callStart] ${call.request().method} ${call.request().url}")
        }

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            Log.d("OkHttp", "[connectStart] -> $inetSocketAddress")
        }

        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
            Log.d("OkHttp", "[connectEnd] protocol=$protocol")
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            Log.d("OkHttp", "[connectionAcquired] $connection")
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            Log.d("OkHttp", "[requestBodyEnd] $byteCount bytes sent")
        }

        override fun responseHeadersStart(call: Call) {
            val elapsedMs = (System.nanoTime() - callStartNanos) / 1_000_000
            Log.d("OkHttp", "[responseHeadersStart] after ${elapsedMs}ms")
        }

        override fun responseBodyEnd(call: Call, byteCount: Long) {
            val elapsedMs = (System.nanoTime() - callStartNanos) / 1_000_000
            Log.d("OkHttp", "[responseBodyEnd] $byteCount bytes in ${elapsedMs}ms")
        }

        override fun callEnd(call: Call) {
            val elapsedMs = (System.nanoTime() - callStartNanos) / 1_000_000
            Log.d("OkHttp", "[callEnd] ${call.request().url} completed in ${elapsedMs}ms")
        }

        override fun callFailed(call: Call, ioe: IOException) {
            val elapsedMs = (System.nanoTime() - callStartNanos) / 1_000_000
            Log.e("OkHttp", "[callFailed] ${call.request().url} after ${elapsedMs}ms", ioe)
        }
    }
}
