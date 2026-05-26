# Batch Printing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the application to receive and print an array of label images from a single server response, showing print progress to the user.

**Architecture:** We will introduce a `PrintItem` data class to hold individual label settings, update `ServerPrintRequest` to hold a list of these items, modify `ApiClient` to parse the `Items` array (including fallback to root-level defaults), implement a `smartPrintBatch` method in `TscPrinterService` with GZIP decompression support, and update `PrinterDialog` to invoke the batch method and display progress.

**Tech Stack:** Kotlin, Android SDK, Compose (for UI), Gson (for JSON parsing), `java.util.zip.GZIPInputStream` (for decompression).

---

### Task 1: Update Data Models

**Files:**
- Modify: `app/src/main/java/com/tsd/ascanner/utils/ServerPrintRequest.kt`

- [ ] **Step 1: Update the models**

Update `ServerPrintRequest.kt` to define `PrintItem` and update `ServerPrintRequest`:

```kotlin
package com.tsd.ascanner.utils

/**
 * Data class representing a single item to print.
 */
data class PrintItem(
    val pictureBase64: String,
    val pictureType: String = "bmp",
    val paperWidthMm: Float = 50f,
    val paperHeightMm: Float = 30f,
    val copies: Int = 1,
    val compression: String = ""
)

/**
 * Data class representing a print request from the server.
 * 
 * When server responds with MessageType="print", the response contains
 * an array of images to print along with paper dimensions.
 */
data class ServerPrintRequest(
    val form: String,
    val formId: String,
    val items: List<PrintItem>,
    val selectOnExit: String = ""
)
```

- [ ] **Step 2: Build the project to verify models compile**

Run: `./gradlew assembleDebug`
Expected: Compile errors in `ApiClient.kt` and `PrinterDialog.kt` due to signature changes.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tsd/ascanner/utils/ServerPrintRequest.kt
git commit -m "feat(print): update data models for batch printing"
```

---

### Task 2: Update API Parsing

**Files:**
- Modify: `app/src/main/java/com/tsd/ascanner/data/net/ApiClient.kt`

- [ ] **Step 1: Implement the parsing logic**

In `app/src/main/java/com/tsd/ascanner/data/net/ApiClient.kt`, locate the block handling `MessageType="print"` (around line 241). Replace it with:

```kotlin
				if (mt != null && mt.equals("print", ignoreCase = true)) {
					val formId = if (obj.has("FormId")) obj.get("FormId").asString else ""
					val selectOnExit = if (obj.has("SelectOnExit")) obj.get("SelectOnExit").asString else ""
					
					val itemsList = mutableListOf<com.tsd.ascanner.utils.PrintItem>()
					
					// Root-level defaults
					val rootPaperWidth = if (obj.has("PaperWidth")) obj.get("PaperWidth").asString.toFloatOrNull() ?: 50f else 50f
					val rootPaperHeight = if (obj.has("PaperHeight")) obj.get("PaperHeight").asString.toFloatOrNull() ?: 30f else 30f
					val rootCopies = if (obj.has("PrintCopies")) obj.get("PrintCopies").asString.toIntOrNull() ?: 1 else 1
					val rootPictureType = if (obj.has("PictureType")) obj.get("PictureType").asString else "bmp"
					val rootCompression = if (obj.has("Compression")) obj.get("Compression").asString else ""
					
					if (obj.has("Items") && obj.get("Items").isJsonArray) {
						val itemsArray = obj.getAsJsonArray("Items")
						for (i in 0 until itemsArray.size()) {
							val itemObj = itemsArray.get(i).asJsonObject
							val picture = if (itemObj.has("Picture")) itemObj.get("Picture").asString else ""
							if (picture.isNotBlank()) {
								val paperWidth = if (itemObj.has("PaperWidth")) itemObj.get("PaperWidth").asString.toFloatOrNull() ?: rootPaperWidth else rootPaperWidth
								val paperHeight = if (itemObj.has("PaperHeight")) itemObj.get("PaperHeight").asString.toFloatOrNull() ?: rootPaperHeight else rootPaperHeight
								val copies = if (itemObj.has("PrintCopies")) itemObj.get("PrintCopies").asString.toIntOrNull() ?: rootCopies else rootCopies
								val pictureType = if (itemObj.has("PictureType")) itemObj.get("PictureType").asString else rootPictureType
								val compression = if (itemObj.has("Compression")) itemObj.get("Compression").asString else rootCompression
								
								itemsList.add(
									com.tsd.ascanner.utils.PrintItem(
										pictureBase64 = picture,
										pictureType = pictureType,
										paperWidthMm = paperWidth,
										paperHeightMm = paperHeight,
										copies = copies,
										compression = compression
									)
								)
							}
						}
					} else {
						// Fallback for single item
						val picture = if (obj.has("Picture")) obj.get("Picture").asString else ""
						if (picture.isNotBlank()) {
							itemsList.add(
								com.tsd.ascanner.utils.PrintItem(
									pictureBase64 = picture,
									pictureType = rootPictureType,
									paperWidthMm = rootPaperWidth,
									paperHeightMm = rootPaperHeight,
									copies = rootCopies,
									compression = rootCompression
								)
							)
						}
					}
					
					if (itemsList.isNotEmpty()) {
						PrintBus.emit(
							ServerPrintRequest(
								form = form ?: "",
								formId = formId,
								items = itemsList,
								selectOnExit = selectOnExit
							)
						)
					}
					return
				}
```

- [ ] **Step 2: Build the project to verify parsing logic**

Run: `./gradlew assembleDebug`
Expected: Compile errors in `PrinterDialog.kt` but `ApiClient.kt` should compile fine.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tsd/ascanner/data/net/ApiClient.kt
git commit -m "feat(print): parse batch printing items from server response"
```

---

### Task 3: Implement Batch Printing in Service

**Files:**
- Modify: `app/src/main/java/com/tsd/ascanner/data/printer/TscPrinterService.kt`

- [ ] **Step 1: Implement decompression and batch logic**

In `app/src/main/java/com/tsd/ascanner/data/printer/TscPrinterService.kt`, add the necessary imports at the top:
```kotlin
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import com.tsd.ascanner.utils.PrintItem
```

Add the `smartPrintBatch` method at the end of the class:

```kotlin
    /**
     * Smart print a batch of items - automatically connects to last printer if needed.
     * Processes each item sequentially. Aborts on first error.
     * Supports gzip compression for the Base64 image.
     */
    suspend fun smartPrintBatch(
        items: List<PrintItem>,
        onProgress: (Int, Int) -> Unit
    ): PrintResult {
        if (connectionState.value != ConnectionState.CONNECTED) {
            val connected = tryAutoConnect()
            if (!connected) {
                Log.d(TAG, "smartPrintBatch: auto-connect failed, need printer selection")
                return PrintResult.NeedPrinterSelection
            }
        }

        val total = items.size
        for ((index, item) in items.withIndex()) {
            try {
                var base64Data = item.pictureBase64
                
                // Handle gzip compression
                if (item.compression.equals("gzip", ignoreCase = true)) {
                    val compressedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val gis = GZIPInputStream(ByteArrayInputStream(compressedBytes))
                    val decompressedBytes = gis.readBytes()
                    base64Data = android.util.Base64.encodeToString(decompressedBytes, android.util.Base64.DEFAULT)
                }

                val success = printFromBase64(
                    base64Data,
                    item.paperWidthMm,
                    item.paperHeightMm,
                    item.copies
                )

                if (!success) {
                    val error = lastError.value ?: "Unknown error on label ${index + 1}"
                    Log.e(TAG, "smartPrintBatch: print failed at index $index - $error")
                    return PrintResult.Error("Ошибка печати этикетки ${index + 1} из $total: $error")
                }
                
                onProgress(index + 1, total)
            } catch (e: Exception) {
                Log.e(TAG, "smartPrintBatch: exception at index $index", e)
                return PrintResult.Error("Ошибка обработки этикетки ${index + 1} из $total: ${e.message}")
            }
        }

        Log.d(TAG, "smartPrintBatch: all prints successful")
        return PrintResult.Success
    }
```

- [ ] **Step 2: Build the project to verify**

Run: `./gradlew assembleDebug`
Expected: Compile errors in `PrinterDialog.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tsd/ascanner/data/printer/TscPrinterService.kt
git commit -m "feat(print): add batch printing and gzip decompression in service"
```

---

### Task 4: Update UI to support batch printing progress

**Files:**
- Modify: `app/src/main/java/com/tsd/ascanner/ui/components/PrinterDialog.kt`

- [ ] **Step 1: Update the dialog logic**

In `app/src/main/java/com/tsd/ascanner/ui/components/PrinterDialog.kt`:

Change the state variables (around line 92) to add progress:
```kotlin
    var printResult by remember { mutableStateOf<String?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var printProgress by remember { mutableStateOf("") }
```

Update `printFromRequest` method (around line 159):
```kotlin
    // Print from server request
    fun printFromRequest() {
        if (printRequest == null) return
        
        scope.launch {
            isPrinting = true
            printResult = null
            printProgress = "Печать 1 из ${printRequest.items.size}..."
            
            val result = printerService.smartPrintBatch(
                items = printRequest.items,
                onProgress = { current, total ->
                    printProgress = "Печать $current из $total..."
                }
            )
            
            isPrinting = false
            
            when (result) {
                is TscPrinterService.PrintResult.Success -> {
                    printResult = "Успешно напечатано (${printRequest.items.size} шт.)"
                    onPrintSuccess()
                }
                is TscPrinterService.PrintResult.Error -> {
                    printResult = result.message
                }
                is TscPrinterService.PrintResult.NeedPrinterSelection -> {
                    printResult = "Принтер не подключен"
                }
            }
        }
    }
```

Update the print button (around line 265). Replace the `Button` with:
```kotlin
                            Button(
                                onClick = { printFromRequest() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = connectionState == TscPrinterService.ConnectionState.CONNECTED && !isPrinting
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isPrinting) printProgress else "Печать (${printRequest.items.size} шт.)")
                            }
```

Update the success message display text (around line 273). Replace:
```kotlin
                        if (printResult != null) {
                            Text(
                                text = printResult!!,
                                color = if (printResult == "Напечатано" || printResult == "Bitmap отправлен на печать" || printResult?.startsWith("Успешно") == true) 
                                    MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
```

- [ ] **Step 2: Build the project to verify everything compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tsd/ascanner/ui/components/PrinterDialog.kt
git commit -m "feat(print): update dialog to show batch printing progress"
```
