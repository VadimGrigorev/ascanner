package com.tsd.ascanner.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Intercepts keyboard-wedge barcode scanner input in [android.app.Activity.dispatchKeyEvent].
 *
 * Scanners in keyboard-wedge mode emit the entire barcode as a rapid burst of key events
 * followed by Enter. This class buffers incoming printable characters and decides whether
 * the sequence is a barcode scan or normal human typing:
 *
 * - **Scan detected**: characters accumulated within [MAX_SCAN_DURATION_MS] and terminated
 *   by Enter with at least [MIN_BARCODE_LENGTH] characters -- emitted to [ScanDataBus].
 * - **Human typing**: characters arriving slowly or buffer flushed by timeout -- replayed
 *   to the view hierarchy via the [superDispatch] callback so text fields receive them.
 */
class KeyboardWedgeInterceptor {

    companion object {
        private const val MAX_SCAN_DURATION_MS = 300L
        private const val FLUSH_TIMEOUT_MS = 200L
        private const val MIN_BARCODE_LENGTH = 4
    }

    private val handler = Handler(Looper.getMainLooper())
    private val bufferedEvents = mutableListOf<KeyEvent>()
    private val barcodeChars = StringBuilder()
    private var firstCharTime = 0L

    private var superDispatchRef: ((KeyEvent) -> Boolean)? = null

    private val flushRunnable = Runnable { flushAsTyping() }

    /**
     * Call from [android.app.Activity.dispatchKeyEvent].
     *
     * @param event the key event to process.
     * @param superDispatch lambda that calls `super.dispatchKeyEvent(e)` to let the
     *        event reach the view hierarchy normally.
     * @return `true` if the event was consumed (buffered or recognised as scan),
     *         `false` if it should be handled by the caller via normal dispatch.
     */
    fun onKeyEvent(event: KeyEvent, superDispatch: (KeyEvent) -> Boolean): Boolean {
        superDispatchRef = superDispatch

        val isPrintable = event.isPrintingKey
        val isEnter = event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

        if (!isPrintable && !isEnter) {
            flushAsTyping()
            return false
        }

        if (isPrintable) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (barcodeChars.isEmpty()) {
                    firstCharTime = SystemClock.uptimeMillis()
                }
                val ch = event.unicodeChar.toChar()
                if (ch.code > 0) {
                    barcodeChars.append(ch)
                }
            }
            bufferedEvents.add(KeyEvent(event))
            resetFlushTimeout()
            return true
        }

        // Enter key
        if (event.action == KeyEvent.ACTION_DOWN) {
            handler.removeCallbacks(flushRunnable)
            val elapsed = SystemClock.uptimeMillis() - firstCharTime
            if (barcodeChars.length >= MIN_BARCODE_LENGTH && elapsed <= MAX_SCAN_DURATION_MS) {
                val barcode = barcodeChars.toString()
                clearBuffer()
                ScanDataBus.emit(barcode)
                return true
            }
            flushAsTyping()
            return superDispatch(event)
        }

        // Enter ACTION_UP after a scan was already emitted (buffer is empty)
        if (event.action == KeyEvent.ACTION_UP && barcodeChars.isEmpty() && bufferedEvents.isEmpty()) {
            return true
        }

        return superDispatch(event)
    }

    private fun resetFlushTimeout() {
        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, FLUSH_TIMEOUT_MS)
    }

    private fun flushAsTyping() {
        handler.removeCallbacks(flushRunnable)
        val dispatch = superDispatchRef ?: return
        val events = bufferedEvents.toList()
        clearBuffer()
        for (e in events) {
            dispatch(e)
        }
    }

    private fun clearBuffer() {
        bufferedEvents.clear()
        barcodeChars.clear()
        firstCharTime = 0L
    }
}
