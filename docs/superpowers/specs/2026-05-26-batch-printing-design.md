# Batch Printing Design

## 1. Overview
The current printing system processes a single image (`Picture` field at the root of the JSON response). The goal is to support printing multiple labels in a single request. The server will send an array of `Items`, each containing its own image data and print settings (width, height, copies, compression). The application will parse this array and sequentially send each label to the printer.

## 2. Models
* **`PrintItem`**: A new data class representing a single label to print.
  * `pictureBase64: String`
  * `pictureType: String`
  * `paperWidthMm: Float`
  * `paperHeightMm: Float`
  * `copies: Int`
  * `compression: String` (e.g., "gzip" or empty/null for no compression)
* **`ServerPrintRequest`**: Updated to contain `val items: List<PrintItem>` instead of root-level picture fields. Retains `form`, `formId`, and `selectOnExit`.

## 3. Parsing (ApiClient)
* When `MessageType` is `"print"`, the client will check for the `Items` array.
* If `Items` exists, it will iterate through the array, extracting settings for each item, and create a list of `PrintItem`.
* If a setting is missing in an item, it will fall back to root-level defaults or standard defaults.
* Emits the updated `ServerPrintRequest` via `PrintBus`.

## 4. Printer Service (TscPrinterService)
* Add a new method: `suspend fun smartPrintBatch(items: List<PrintItem>, onProgress: (Int, Int) -> Unit): PrintResult`
* The method will loop over the `items` array.
* **Decompression**: Before decoding the Base64 string into a Bitmap, check if `compression == "gzip"`. If so, decode the Base64, pass the byte array through a `GZIPInputStream`, and then decode the resulting bytes into a `Bitmap`.
* Print each label sequentially using existing internal printing methods.
* If any label fails to print, the loop is immediately aborted, and `PrintResult.Error` is returned.
* Calls `onProgress(currentIndex + 1, totalCount)` after each successful label.

## 5. UI (PrinterDialog)
* Update the dialog to display batch progress when multiple labels are present.
* The "Print" button will call `printerService.smartPrintBatch()`.
* While printing, the button is disabled, and a progress text is shown (e.g., "Печать 1 из 5...").
* On error, the process stops and the error message is displayed. To retry, the user will have to request it from the server again.
