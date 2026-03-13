package com.tsd.ascanner.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Centralized bus for barcode/QR scan results delivered via intent-based
 * scanning (DataWedge, Newland broadcasts). Screens collect [scans] to
 * handle incoming barcodes without relying on keyboard-wedge input.
 */
object ScanDataBus {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scans = _scans.asSharedFlow()

    fun emit(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isNotEmpty()) _scans.tryEmit(trimmed)
    }
}
