# Silent Printing with Progress Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement silent printing mode that attempts to auto-print to the last used printer with a blocking progress overlay, falling back to the manual dialog on failure or cancellation.

**Architecture:** We will add `autoPrint` flag to `ServerPrintRequest`, parsed from `"PrintDialog": "0"`. In `MainActivity`, this flag will toggle a new `AutoPrintOverlay` composable instead of the standard `PrinterDialog`. The overlay will launch `smartPrintBatch`, and on error or cancel, gracefully transition to the manual `PrinterDialog`.

**Tech Stack:** Kotlin, Android SDK, Compose.

---

### Task 1: Update Data Model and Parsing

**Files:**
- Modify: `app/src/main/java/com/tsd/ascanner/utils/ServerPrintRequest.kt`
- Modify: `app/src/main/java/com/tsd/ascanner/data/net/ApiClient.kt`

- [ ] **Step 1: Update the data model**

In `app/src/main/java/com/tsd/ascanner/utils/ServerPrintRequest.kt`, add the `autoPrint` parameter:

```kotlin
data class ServerPrintRequest(
    val form: String,
    val formId: String,
    val items: List<PrintItem>,
    val selectOnExit: String = "",
    val autoPrint: Boolean = false
)
```

- [ ] **Step 2: Update the parser**

In `app/src/main/java/com/tsd/ascanner/data/net/ApiClient.kt`, locate the print parsing logic (around line 241). Update the extraction of root-level defaults to read `PrintDialog` and construct the `ServerPrintRequest` with `autoPrint`:

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
					
					val printDialogVal = if (obj.has("PrintDialog")) obj.get("PrintDialog").asString else "1"
					val autoPrint = printDialogVal == "0"
					
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
							com.tsd.ascanner.utils.ServerPrintRequest(
								form = form ?: "",
								formId = formId,
								items = itemsList,
								selectOnExit = selectOnExit,
								autoPrint = autoPrint
							)
						)
					}
					return
				}
```

- [ ] **Step 3: Verify build**
Run: `./gradlew assembleDebug` (ignore UI compile errors if any)

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/tsd/ascanner/utils/ServerPrintRequest.kt app/src/main/java/com/tsd/ascanner/data/net/ApiClient.kt
git commit -m "feat(print): parse PrintDialog flag for auto printing"
```

---

### Task 2: Create AutoPrintOverlay Component

**Files:**
- Create: `app/src/main/java/com/tsd/ascanner/ui/components/AutoPrintOverlay.kt`

- [ ] **Step 1: Create the composable**

Create `app/src/main/java/com/tsd/ascanner/ui/components/AutoPrintOverlay.kt`:

```kotlin
package com.tsd.ascanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.data.printer.TscPrinterService
import com.tsd.ascanner.utils.ServerPrintRequest
import kotlinx.coroutines.Job

@Composable
fun AutoPrintOverlay(
    visible: Boolean,
    printRequest: ServerPrintRequest?,
    app: AScannerApp,
    onPrintSuccess: () -> Unit,
    onFallbackToManual: () -> Unit
) {
    if (!visible || printRequest == null) return

    val printerService = app.printerService
    var printProgress by remember { mutableStateOf("Подключение к принтеру...") }
    var printJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(printRequest) {
        printProgress = "Подключение к принтеру..."
        printJob = kotlinx.coroutines.launch {
            val result = printerService.smartPrintBatch(
                items = printRequest.items,
                onProgress = { current, total ->
                    printProgress = "Печать $current из $total..."
                }
            )

            when (result) {
                is TscPrinterService.PrintResult.Success -> {
                    onPrintSuccess()
                }
                else -> {
                    // NeedPrinterSelection or Error -> fallback to manual dialog
                    onFallbackToManual()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Ignored */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = printProgress,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        printJob?.cancel()
                        onFallbackToManual()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Отмена")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**
Run: `./gradlew assembleDebug`

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/tsd/ascanner/ui/components/AutoPrintOverlay.kt
git commit -m "feat(print): create AutoPrintOverlay component for silent printing"
```

---

### Task 3: Integrate Overlay in MainActivity

**Files:**
- Modify: `app/src/main/java/com/tsd/ascanner/MainActivity.kt`

- [ ] **Step 1: Add new state and observation logic**

In `app/src/main/java/com/tsd/ascanner/MainActivity.kt`, find the variable declarations around line 156:
```kotlin
				var currentPrintRequest by remember { mutableStateOf<ServerPrintRequest?>(null) }
				var showPrinterDialog by remember { mutableStateOf(false) }
```
Add the new state:
```kotlin
				var showAutoPrintDialog by remember { mutableStateOf(false) }
```

In the `PrintBus.events.collectLatest` block (around line 183):
```kotlin
					PrintBus.events.collectLatest { printRequest ->
						currentPrintRequest = printRequest
						if (printRequest.autoPrint) {
							showAutoPrintDialog = true
							showPrinterDialog = false
						} else {
							showPrinterDialog = true
							showAutoPrintDialog = false
						}
					}
```

- [ ] **Step 2: Add the AutoPrintOverlay composable to the UI tree**

Find the `PrinterDialog` usage (around line 743). Place the `AutoPrintOverlay` right above it:
```kotlin
						// Global auto-print overlay (for MessageType="print" with PrintDialog="0")
						AutoPrintOverlay(
							visible = showAutoPrintDialog,
							printRequest = currentPrintRequest,
							app = application as AScannerApp,
							onPrintSuccess = {
								val req = currentPrintRequest
								showAutoPrintDialog = false
								currentPrintRequest = null
								
								// Process SelectOnExit
								if (req != null && req.selectOnExit.isNotBlank()) {
									SelectBus.emit(
										ServerSelectRequest(
											form = req.form,
											formId = req.formId,
											barcode = req.selectOnExit
										)
									)
								}
							},
							onFallbackToManual = {
								showAutoPrintDialog = false
								showPrinterDialog = true
							}
						)

						// Global printer dialog (for MessageType="print" manual fallback or PrintDialog="1")
						PrinterDialog(
```

Make sure to add the import at the top of the file:
```kotlin
import com.tsd.ascanner.ui.components.AutoPrintOverlay
```

- [ ] **Step 3: Verify build**
Run: `./gradlew assembleDebug`

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/tsd/ascanner/MainActivity.kt
git commit -m "feat(print): integrate AutoPrintOverlay in MainActivity"
```
