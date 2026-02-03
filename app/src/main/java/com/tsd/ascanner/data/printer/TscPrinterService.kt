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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
     * Pixel value: 1 = black (print), 0 = white (no print)
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
                
                // If dark pixel (luminance < 128), set bit to 1 (black/print)
                if (luminance < 128) {
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
            isAntiAlias = true
        }
        
        // Fill with white background
        canvas.drawColor(Color.WHITE)
        
        // Draw black border
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRect(2f, 2f, LABEL_WIDTH_DOTS - 2f, LABEL_HEIGHT_DOTS - 2f, paint)
        
        // Draw title text
        paint.style = Paint.Style.FILL
        paint.textSize = 48f
        paint.typeface = Typeface.DEFAULT_BOLD
        val title = "TSC RE310"
        val titleWidth = paint.measureText(title)
        canvas.drawText(title, (LABEL_WIDTH_DOTS - titleWidth) / 2, 60f, paint)
        
        // Draw subtitle
        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT
        val subtitle = "Bitmap Test"
        val subtitleWidth = paint.measureText(subtitle)
        canvas.drawText(subtitle, (LABEL_WIDTH_DOTS - subtitleWidth) / 2, 110f, paint)
        
        // Draw size info
        paint.textSize = 24f
        val sizeInfo = "${LABEL_WIDTH_MM}x${LABEL_HEIGHT_MM} mm"
        val sizeWidth = paint.measureText(sizeInfo)
        canvas.drawText(sizeInfo, (LABEL_WIDTH_DOTS - sizeWidth) / 2, 150f, paint)
        
        // Draw a small test pattern (checkerboard) at bottom
        paint.style = Paint.Style.FILL
        val patternSize = 16
        val patternStartX = (LABEL_WIDTH_DOTS - patternSize * 10) / 2
        val patternStartY = 180
        for (row in 0 until 3) {
            for (col in 0 until 10) {
                if ((row + col) % 2 == 0) {
                    canvas.drawRect(
                        (patternStartX + col * patternSize).toFloat(),
                        (patternStartY + row * patternSize).toFloat(),
                        (patternStartX + (col + 1) * patternSize).toFloat(),
                        (patternStartY + (row + 1) * patternSize).toFloat(),
                        paint
                    )
                }
            }
        }
        
        Log.d(TAG, "Created test bitmap: ${bitmap.width}x${bitmap.height}")
        
        // Print the bitmap
        val result = printBitmap(bitmap)
        
        // Clean up
        bitmap.recycle()
        
        return result
    }
    
    /**
     * Clear any error state
     */
    fun clearError() {
        _lastError.value = null
    }
}
