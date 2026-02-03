package com.tsd.ascanner.data.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Service for communicating with TSC RE310 label printer via Bluetooth.
 * 
 * The TSC RE310 supports CPCL and TSPL command languages.
 * This implementation uses CPCL commands which are common for mobile TSC printers.
 */
class TscPrinterService(private val context: Context) {

    companion object {
        private const val TAG = "TscPrinterService"
        
        // Standard SPP (Serial Port Profile) UUID for Bluetooth printers
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // TSC printer names typically contain these strings
        private val TSC_PRINTER_NAMES = listOf("TSC", "RE310", "RE-310", "Alpha")
        
        // Label size configuration for 50x30 mm labels at 203 DPI (8 dots/mm)
        const val LABEL_WIDTH_MM = 50
        const val LABEL_HEIGHT_MM = 30
        const val DPI = 203
        const val DOTS_PER_MM = 8  // 203 DPI ≈ 8 dots/mm
        
        // Label size in dots
        const val LABEL_WIDTH_DOTS = LABEL_WIDTH_MM * DOTS_PER_MM   // 400 dots
        const val LABEL_HEIGHT_DOTS = LABEL_HEIGHT_MM * DOTS_PER_MM // 240 dots
        
        // Width in bytes for BITMAP command (width must be divisible by 8)
        const val LABEL_WIDTH_BYTES = LABEL_WIDTH_DOTS / 8  // 50 bytes
        
        // SharedPreferences for storing last printer address
        private const val PREFS_NAME = "printer_settings"
        private const val KEY_LAST_PRINTER_ADDRESS = "last_printer_address"
    }
    
    // SharedPreferences for persisting printer settings
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Result of a smart print operation
     */
    sealed class PrintResult {
        /** Print was successful */
        object Success : PrintResult()
        /** No printer configured or auto-connect failed - need to show printer selection dialog */
        object NeedPrinterSelection : PrintResult()
        /** Print failed with an error */
        data class Error(val message: String) : PrintResult()
    }

    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    
    private var _connectedDevice: BluetoothDevice? = null
    val connectedDevice: BluetoothDevice? get() = _connectedDevice

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    /**
     * Check if Bluetooth permissions are granted
     */
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == 
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == 
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == 
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if Bluetooth is enabled on the device
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Save the address of the last successfully connected printer
     */
    private fun saveLastPrinterAddress(address: String) {
        prefs.edit().putString(KEY_LAST_PRINTER_ADDRESS, address).apply()
        Log.d(TAG, "Saved last printer address: $address")
    }
    
    /**
     * Get the address of the last successfully connected printer
     */
    fun getLastPrinterAddress(): String? {
        return prefs.getString(KEY_LAST_PRINTER_ADDRESS, null)
    }
    
    /**
     * Try to automatically connect to the last used printer.
     * 
     * @return true if already connected or successfully connected to last printer,
     *         false if no last printer saved, device not found, or connection failed
     */
    @SuppressLint("MissingPermission")
    suspend fun tryAutoConnect(): Boolean {
        // Already connected - nothing to do
        if (isConnected()) {
            Log.d(TAG, "tryAutoConnect: already connected")
            return true
        }
        
        // Check permissions
        if (!hasBluetoothPermissions()) {
            Log.d(TAG, "tryAutoConnect: no Bluetooth permissions")
            return false
        }
        
        // Get last printer address
        val lastAddress = getLastPrinterAddress()
        if (lastAddress.isNullOrBlank()) {
            Log.d(TAG, "tryAutoConnect: no last printer address saved")
            return false
        }
        
        Log.d(TAG, "tryAutoConnect: attempting to connect to $lastAddress")
        
        // Find the device in paired devices
        val device = getPairedDevices().find { it.address == lastAddress }
        if (device == null) {
            Log.d(TAG, "tryAutoConnect: device $lastAddress not found in paired devices")
            return false
        }
        
        // Try to connect
        return connect(device)
    }

    /**
     * Get list of paired Bluetooth devices
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) {
            _lastError.value = "Bluetooth permissions not granted"
            return emptyList()
        }
        
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Get paired devices that look like TSC printers
     */
    @SuppressLint("MissingPermission")
    fun getTscPrinters(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) {
            return emptyList()
        }
        
        return getPairedDevices().filter { device ->
            val name = device.name?.uppercase() ?: ""
            TSC_PRINTER_NAMES.any { name.contains(it.uppercase()) }
        }
    }

    /**
     * Connect to a Bluetooth device
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            _lastError.value = "Bluetooth permissions not granted"
            return@withContext false
        }

        disconnect()
        
        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null

        try {
            Log.d(TAG, "Connecting to ${device.name} (${device.address})")
            
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket = socket
            
            // Cancel discovery to speed up connection
            bluetoothAdapter?.cancelDiscovery()
            
            socket.connect()
            outputStream = socket.outputStream
            _connectedDevice = device
            _connectionState.value = ConnectionState.CONNECTED
            
            // Save this printer as the last used one for auto-connect
            saveLastPrinterAddress(device.address)
            
            Log.d(TAG, "Connected to ${device.name}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            _lastError.value = "Ошибка подключения: ${e.message}"
            _connectionState.value = ConnectionState.DISCONNECTED
            disconnect()
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during connection", e)
            _lastError.value = "Нет разрешений Bluetooth"
            _connectionState.value = ConnectionState.DISCONNECTED
            false
        }
    }

    /**
     * Disconnect from the current device
     */
    fun disconnect() {
        try {
            outputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing output stream", e)
        }
        
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        
        outputStream = null
        bluetoothSocket = null
        _connectedDevice = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Check if connected to a printer
     */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true && _connectionState.value == ConnectionState.CONNECTED
    }

    /**
     * Send raw data to the printer
     */
    suspend fun sendRawData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            _lastError.value = "Принтер не подключен"
            return@withContext false
        }

        try {
            outputStream?.write(data)
            outputStream?.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data", e)
            _lastError.value = "Ошибка отправки: ${e.message}"
            disconnect()
            false
        }
    }

    /**
     * Send CPCL command string to the printer
     */
    suspend fun sendCpclCommand(command: String): Boolean {
        return sendRawData(command.toByteArray(Charsets.US_ASCII))
    }

    /**
     * Print a test label using CPCL commands
     * 
     * CPCL is the standard command language for TSC mobile printers like RE310.
     * Label size: 80mm width (640 dots at 203 DPI)
     */
    suspend fun printTestLabel(): Boolean {
        if (!isConnected()) {
            _lastError.value = "Принтер не подключен"
            return false
        }

        // CPCL test label command
        // Format: ! offset_x unit_height width_dots label_height qty
        val cpclCommand = buildString {
            // Start CPCL label: offset=0, unit_height=200, width=640 dots (~80mm), height=200
            append("! 0 200 200 210 1\r\n")
            
            // Set encoding for Cyrillic (if supported)
            append("ENCODING UTF-8\r\n")
            
            // Draw a box border
            append("BOX 10 10 630 200 2\r\n")
            
            // Print text: TEXT font size x y data
            // Font 4 = 12x20 dots, font 5 = 16x32 dots, font 7 = 24x48 dots
            append("TEXT 7 0 50 30 TSC RE310\r\n")
            append("TEXT 4 0 50 90 Test Label\r\n")
            append("TEXT 4 0 50 130 AScanner App\r\n")
            
            // Print a barcode: BARCODE type width ratio height x y data
            append("BARCODE 128 1 1 40 50 170 TEST123\r\n")
            
            // Print the label
            append("FORM\r\n")
            append("PRINT\r\n")
        }

        Log.d(TAG, "Sending CPCL command:\n$cpclCommand")
        return sendCpclCommand(cpclCommand)
    }

    /**
     * Print a simple text label using TSPL commands (alternative to CPCL)
     * 
     * Some TSC printers respond better to TSPL commands.
     */
    suspend fun printTestLabelTspl(): Boolean {
        if (!isConnected()) {
            _lastError.value = "Принтер не подключен"
            return false
        }

        // TSPL test label command
        val tsplCommand = buildString {
            // Set label size (width, height in mm)
            append("SIZE 80 mm, 30 mm\r\n")
            
            // Set gap between labels
            append("GAP 2 mm, 0 mm\r\n")
            
            // Set print direction
            append("DIRECTION 1\r\n")
            
            // Clear buffer
            append("CLS\r\n")
            
            // Print text: TEXT x,y,"font",rotation,x-mult,y-mult,"content"
            append("TEXT 50,20,\"3\",0,1,1,\"TSC RE310 Test\"\r\n")
            append("TEXT 50,60,\"2\",0,1,1,\"AScanner Application\"\r\n")
            
            // Print barcode: BARCODE x,y,"type",height,readable,rotation,narrow,wide,"content"
            append("BARCODE 50,100,\"128\",50,1,0,2,4,\"TEST123\"\r\n")
            
            // Print one label
            append("PRINT 1\r\n")
        }

        Log.d(TAG, "Sending TSPL command:\n$tsplCommand")
        return sendCpclCommand(tsplCommand)
    }

    /**
     * Print a custom label with provided text and barcode
     */
    suspend fun printLabel(
        title: String,
        subtitle: String? = null,
        barcodeData: String? = null
    ): Boolean {
        if (!isConnected()) {
            _lastError.value = "Принтер не подключен"
            return false
        }

        val tsplCommand = buildString {
            append("SIZE 80 mm, 40 mm\r\n")
            append("GAP 2 mm, 0 mm\r\n")
            append("DIRECTION 1\r\n")
            append("CLS\r\n")
            
            // Title
            append("TEXT 30,20,\"3\",0,1,1,\"$title\"\r\n")
            
            // Subtitle if provided
            if (!subtitle.isNullOrBlank()) {
                append("TEXT 30,60,\"2\",0,1,1,\"$subtitle\"\r\n")
            }
            
            // Barcode if provided
            if (!barcodeData.isNullOrBlank()) {
                val yPos = if (subtitle.isNullOrBlank()) 60 else 100
                append("BARCODE 30,$yPos,\"128\",50,1,0,2,4,\"$barcodeData\"\r\n")
            }
            
            append("PRINT 1\r\n")
        }

        return sendCpclCommand(tsplCommand)
    }

    /**
     * Convert a Bitmap to 1-bit monochrome byte array for TSPL BITMAP command.
     * 
     * The bitmap is scaled to label size and converted to 1-bit per pixel format.
     * Each byte contains 8 horizontal pixels (MSB first).
     * Pixel value (for this printer/TSPL BITMAP): 0 = black (print), 1 = white (no print)
     * 
     * @param bitmap Source bitmap (any size, will be scaled)
     * @return ByteArray in 1-bit monochrome format
     */
    private fun bitmapToMonochrome(bitmap: Bitmap): ByteArray {
        // Scale bitmap to label size
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            LABEL_WIDTH_DOTS,
            LABEL_HEIGHT_DOTS,
            true
        )
        
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val widthBytes = (width + 7) / 8  // Round up to nearest byte
        
        val result = ByteArray(widthBytes * height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = scaledBitmap.getPixel(x, y)
                
                // Calculate luminance (grayscale value)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                // RE310/TSPL BITMAP data is inverted compared to common assumptions:
                // set bit to 1 for WHITE pixels (no print). Black stays 0 (print).
                if (luminance >= 128) {
                    val byteIndex = y * widthBytes + (x / 8)
                    val bitIndex = 7 - (x % 8)  // MSB first
                    result[byteIndex] = (result[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }
        
        // Recycle scaled bitmap if it's different from original
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return result
    }
    
    /**
     * Print a bitmap image using TSPL BITMAP command.
     * 
     * The bitmap will be scaled to fit the label size (50x30 mm = 400x240 dots)
     * and converted to 1-bit monochrome format.
     * 
     * @param bitmap The bitmap to print
     * @return true if sent successfully
     */
    suspend fun printBitmap(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            _lastError.value = "Принтер не подключен"
            return@withContext false
        }
        
        try {
            // Convert bitmap to monochrome bytes
            val bitmapData = bitmapToMonochrome(bitmap)
            
            Log.d(TAG, "Printing bitmap: ${LABEL_WIDTH_DOTS}x${LABEL_HEIGHT_DOTS} dots, ${bitmapData.size} bytes")
            
            // Build TSPL command sequence
            val output = ByteArrayOutputStream()
            
            // Set label size
            output.write("SIZE $LABEL_WIDTH_MM mm, $LABEL_HEIGHT_MM mm\r\n".toByteArray(Charsets.US_ASCII))
            
            // Set gap between labels
            output.write("GAP 2 mm, 0 mm\r\n".toByteArray(Charsets.US_ASCII))
            
            // Set print direction
            output.write("DIRECTION 1\r\n".toByteArray(Charsets.US_ASCII))
            
            // Clear buffer
            output.write("CLS\r\n".toByteArray(Charsets.US_ASCII))
            
            // BITMAP command: BITMAP x, y, width_bytes, height, mode, data
            // mode 0 = overwrite
            val bitmapCmd = "BITMAP 0,0,$LABEL_WIDTH_BYTES,$LABEL_HEIGHT_DOTS,0,"
            output.write(bitmapCmd.toByteArray(Charsets.US_ASCII))
            
            // Write raw bitmap data
            output.write(bitmapData)
            
            // End of bitmap data and print command
            output.write("\r\nPRINT 1\r\n".toByteArray(Charsets.US_ASCII))
            
            // Send all data
            val success = sendRawData(output.toByteArray())
            
            if (success) {
                Log.d(TAG, "Bitmap sent successfully")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error printing bitmap", e)
            _lastError.value = "Ошибка печати: ${e.message}"
            false
        }
    }
    
    /**
     * Generate and print a test bitmap with text and a border.
     * 
     * This creates a simple test image to verify bitmap printing works correctly.
     * 
     * @return true if sent successfully
     */
    suspend fun printTestBitmap(): Boolean {
        if (!isConnected()) {
            _lastError.value = "Принтер не подключен"
            return false
        }
        
        // Create a bitmap with label dimensions
        val bitmap = Bitmap.createBitmap(
            LABEL_WIDTH_DOTS,
            LABEL_HEIGHT_DOTS,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = false  // Disable anti-aliasing for sharper lines
        }
        
        // Fill with white background
        canvas.drawColor(Color.WHITE)
        
        // Draw thin corner marks instead of full border (saves ink)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        val cornerSize = 20f
        // Top-left corner
        canvas.drawLine(0f, 0f, cornerSize, 0f, paint)
        canvas.drawLine(0f, 0f, 0f, cornerSize, paint)
        // Top-right corner
        canvas.drawLine(LABEL_WIDTH_DOTS - cornerSize, 0f, LABEL_WIDTH_DOTS.toFloat(), 0f, paint)
        canvas.drawLine(LABEL_WIDTH_DOTS - 1f, 0f, LABEL_WIDTH_DOTS - 1f, cornerSize, paint)
        // Bottom-left corner
        canvas.drawLine(0f, LABEL_HEIGHT_DOTS - 1f, cornerSize, LABEL_HEIGHT_DOTS - 1f, paint)
        canvas.drawLine(0f, LABEL_HEIGHT_DOTS - cornerSize, 0f, LABEL_HEIGHT_DOTS.toFloat(), paint)
        // Bottom-right corner
        canvas.drawLine(LABEL_WIDTH_DOTS - cornerSize, LABEL_HEIGHT_DOTS - 1f, LABEL_WIDTH_DOTS.toFloat(), LABEL_HEIGHT_DOTS - 1f, paint)
        canvas.drawLine(LABEL_WIDTH_DOTS - 1f, LABEL_HEIGHT_DOTS - cornerSize, LABEL_WIDTH_DOTS - 1f, LABEL_HEIGHT_DOTS.toFloat(), paint)
        
        // Draw minimal text
        paint.style = Paint.Style.FILL
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT
        
        val text = "Test OK"
        val textWidth = paint.measureText(text)
        canvas.drawText(text, (LABEL_WIDTH_DOTS - textWidth) / 2, (LABEL_HEIGHT_DOTS + 10) / 2f, paint)
        
        Log.d(TAG, "Created test bitmap: ${bitmap.width}x${bitmap.height}")
        
        // Print the bitmap
        val result = printBitmap(bitmap)
        
        // Clean up
        bitmap.recycle()
        
        return result
    }
    
    /**
     * Smart print a bitmap - automatically connects to last printer if needed.
     * 
     * This method tries to:
     * 1. Use existing connection if available
     * 2. Auto-connect to last used printer if not connected
     * 3. Return NeedPrinterSelection if auto-connect fails
     * 
     * @param bitmap The bitmap to print
     * @return PrintResult indicating success, need for printer selection, or error
     */
    suspend fun smartPrintBitmap(bitmap: Bitmap): PrintResult {
        // Try to auto-connect if not connected
        if (!tryAutoConnect()) {
            Log.d(TAG, "smartPrintBitmap: auto-connect failed, need printer selection")
            return PrintResult.NeedPrinterSelection
        }
        
        // Now we're connected - try to print
        return if (printBitmap(bitmap)) {
            Log.d(TAG, "smartPrintBitmap: print successful")
            PrintResult.Success
        } else {
            val error = lastError.value ?: "Ошибка печати"
            Log.e(TAG, "smartPrintBitmap: print failed - $error")
            PrintResult.Error(error)
        }
    }
    
    /**
     * Smart print a test bitmap - automatically connects to last printer if needed.
     * 
     * Convenience method that generates a test bitmap and prints it using smartPrintBitmap.
     * 
     * @return PrintResult indicating success, need for printer selection, or error
     */
    suspend fun smartPrintTestBitmap(): PrintResult {
        // Try to auto-connect if not connected
        if (!tryAutoConnect()) {
            Log.d(TAG, "smartPrintTestBitmap: auto-connect failed, need printer selection")
            return PrintResult.NeedPrinterSelection
        }
        
        // Now we're connected - try to print test bitmap
        return if (printTestBitmap()) {
            Log.d(TAG, "smartPrintTestBitmap: print successful")
            PrintResult.Success
        } else {
            val error = lastError.value ?: "Ошибка печати"
            Log.e(TAG, "smartPrintTestBitmap: print failed - $error")
            PrintResult.Error(error)
        }
    }
    
    /**
     * Decode a Base64-encoded image string to a Bitmap.
     * 
     * @param base64 The Base64-encoded image data
     * @return Bitmap or null if decoding fails
     */
    private fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Base64 image", e)
            null
        }
    }
    
    /**
     * Convert a Bitmap to 1-bit monochrome byte array with custom dimensions.
     * 
     * @param bitmap Source bitmap
     * @param targetWidthDots Target width in dots
     * @param targetHeightDots Target height in dots
     * @return ByteArray in 1-bit monochrome format
     */
    private fun bitmapToMonochromeWithSize(bitmap: Bitmap, targetWidthDots: Int, targetHeightDots: Int): ByteArray {
        // Scale bitmap to target size
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidthDots, targetHeightDots, true)
        
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val widthBytes = (width + 7) / 8  // Round up to nearest byte
        
        val result = ByteArray(widthBytes * height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = scaledBitmap.getPixel(x, y)
                
                // Calculate luminance (grayscale value)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                // Same inversion as bitmapToMonochrome(): 1 = white (no print), 0 = black (print)
                if (luminance >= 128) {
                    val byteIndex = y * widthBytes + (x / 8)
                    val bitIndex = 7 - (x % 8)  // MSB first
                    result[byteIndex] = (result[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }
        
        // Recycle scaled bitmap if it's different from original
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return result
    }
    
    /**
     * Print a bitmap with custom paper dimensions.
     * 
     * @param bitmap The bitmap to print
     * @param widthMm Paper width in millimeters
     * @param heightMm Paper height in millimeters
     * @param copies Number of copies to print
     * @return true if sent successfully
     */
    suspend fun printBitmapWithSize(
        bitmap: Bitmap,
        widthMm: Float,
        heightMm: Float,
        copies: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            _lastError.value = "Принтер не подключен"
            return@withContext false
        }
        
        try {
            // Calculate dimensions in dots
            val widthDots = (widthMm * DOTS_PER_MM).toInt()
            val heightDots = (heightMm * DOTS_PER_MM).toInt()
            val widthBytes = (widthDots + 7) / 8
            
            // Convert bitmap to monochrome bytes with custom size
            val bitmapData = bitmapToMonochromeWithSize(bitmap, widthDots, heightDots)
            
            Log.d(TAG, "Printing bitmap: ${widthDots}x${heightDots} dots (${widthMm}x${heightMm} mm), ${bitmapData.size} bytes, $copies copies")
            
            // Build TSPL command sequence
            val output = ByteArrayOutputStream()
            
            // Set label size
            output.write("SIZE ${widthMm.toInt()} mm, ${heightMm.toInt()} mm\r\n".toByteArray(Charsets.US_ASCII))
            
            // Set gap between labels
            output.write("GAP 2 mm, 0 mm\r\n".toByteArray(Charsets.US_ASCII))
            
            // Set print direction
            output.write("DIRECTION 1\r\n".toByteArray(Charsets.US_ASCII))
            
            // Clear buffer
            output.write("CLS\r\n".toByteArray(Charsets.US_ASCII))
            
            // BITMAP command: BITMAP x, y, width_bytes, height, mode, data
            val bitmapCmd = "BITMAP 0,0,$widthBytes,$heightDots,0,"
            output.write(bitmapCmd.toByteArray(Charsets.US_ASCII))
            
            // Write raw bitmap data
            output.write(bitmapData)
            
            // End of bitmap data and print command with copies
            output.write("\r\nPRINT $copies\r\n".toByteArray(Charsets.US_ASCII))
            
            // Send all data
            val success = sendRawData(output.toByteArray())
            
            if (success) {
                Log.d(TAG, "Bitmap sent successfully")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error printing bitmap", e)
            _lastError.value = "Ошибка печати: ${e.message}"
            false
        }
    }
    
    /**
     * Print a Base64-encoded image with custom paper dimensions.
     * 
     * @param base64 The Base64-encoded image data
     * @param widthMm Paper width in millimeters
     * @param heightMm Paper height in millimeters
     * @param copies Number of copies to print
     * @return true if sent successfully
     */
    suspend fun printFromBase64(
        base64: String,
        widthMm: Float,
        heightMm: Float,
        copies: Int = 1
    ): Boolean {
        val bitmap = decodeBase64ToBitmap(base64)
        if (bitmap == null) {
            _lastError.value = "Не удалось декодировать изображение"
            return false
        }
        
        val result = printBitmapWithSize(bitmap, widthMm, heightMm, copies)
        bitmap.recycle()
        return result
    }
    
    /**
     * Smart print from Base64 - automatically connects to last printer if needed.
     * 
     * @param base64 The Base64-encoded image data
     * @param widthMm Paper width in millimeters
     * @param heightMm Paper height in millimeters
     * @param copies Number of copies to print
     * @return PrintResult indicating success, need for printer selection, or error
     */
    suspend fun smartPrintFromBase64(
        base64: String,
        widthMm: Float,
        heightMm: Float,
        copies: Int = 1
    ): PrintResult {
        // Try to auto-connect if not connected
        if (!tryAutoConnect()) {
            Log.d(TAG, "smartPrintFromBase64: auto-connect failed, need printer selection")
            return PrintResult.NeedPrinterSelection
        }
        
        // Now we're connected - try to print
        return if (printFromBase64(base64, widthMm, heightMm, copies)) {
            Log.d(TAG, "smartPrintFromBase64: print successful")
            PrintResult.Success
        } else {
            val error = lastError.value ?: "Ошибка печати"
            Log.e(TAG, "smartPrintFromBase64: print failed - $error")
            PrintResult.Error(error)
        }
    }
    
    /**
     * Clear any error state
     */
    fun clearError() {
        _lastError.value = null
    }
}
