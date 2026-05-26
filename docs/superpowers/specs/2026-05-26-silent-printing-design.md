# Silent Printing with Progress Overlay Design

## 1. Overview
The server can now send a `"PrintDialog": "0"` flag in the `MessageType="print"` payload. When this flag is present, the application should attempt to print the labels automatically using the last successfully connected printer, without showing the standard manual print dialog. 

During this "silent" (auto) print, a blocking modal dialog with a progress indicator (spinner) and a "Cancel" button must be shown to the user. All background touches are ignored.

If the auto-print fails (e.g., cannot connect to the last printer, out of paper) or if the user presses "Cancel", the auto-print process stops and the system falls back to opening the standard manual `PrinterDialog`.

## 2. Models
* **`ServerPrintRequest`**: Add a new boolean field `val autoPrint: Boolean = false`.
* **`ApiClient`**: Parse `"PrintDialog"`. If its value is `"0"`, set `autoPrint = true` in the `ServerPrintRequest`. If it's `"1"` or missing, default to `false`.

## 3. UI Flow (`MainActivity` / Compose)
Currently, `MainActivity` observes `PrintBus.events` and sets `showPrinterDialog = true` when a request arrives.

We will introduce a new state: `showAutoPrintDialog`.
When a `ServerPrintRequest` arrives:
1. If `request.autoPrint == true`, we set `showAutoPrintDialog = true`.
2. If `request.autoPrint == false`, we set `showPrinterDialog = true` (legacy behavior).

### 3.1. `AutoPrintOverlay` (New Compose Component)
A new composable that blocks the screen.
* **UI**: A centered card with text (e.g., "Подключение к принтеру..." -> "Печать 1 из N..."), a `CircularProgressIndicator`, and an "Отмена" (Cancel) button. The `Dialog` should have `properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`.
* **Behavior**:
  * On launch, it starts a coroutine to call `printerService.smartPrintBatch(items, onProgress)`.
  * **Success**: If `PrintResult.Success` is returned, the overlay closes, and `onPrintSuccess` is triggered (which handles `SelectOnExit`).
  * **Error / NeedPrinterSelection**: If the print fails or the last printer isn't available, the overlay closes and triggers a fallback callback `onFallbackToManual`.
  * **Cancel Button**: If the user clicks "Отмена", the coroutine is cancelled, the overlay closes, and the fallback callback `onFallbackToManual` is triggered.

### 3.2. Fallback Handling
When `onFallbackToManual` is called from the `AutoPrintOverlay`, `MainActivity` will simply set `showAutoPrintDialog = false` and set `showPrinterDialog = true`, seamlessly transitioning the user to the standard manual printer selection screen.

## 4. Printer Service (`TscPrinterService`)
The existing `smartPrintBatch` method is almost perfect for this. It already attempts to auto-connect to the last printer:
```kotlin
if (connectionState.value != ConnectionState.CONNECTED) {
    val connected = tryAutoConnect() // connects to last used printer
    if (!connected) return PrintResult.NeedPrinterSelection
}
```
Because `smartPrintBatch` handles its own auto-connection and returns `NeedPrinterSelection` or `Error`, the UI `AutoPrintOverlay` can directly use the result of this method to decide whether to fall back to the manual dialog.

No changes to the core TSPL/CPCL logic are required. The only minor enhancement might be ensuring `smartPrintBatch` properly supports coroutine cancellation so the "Cancel" button can abort the print loop quickly.