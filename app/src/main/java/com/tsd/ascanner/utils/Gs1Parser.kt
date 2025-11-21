package com.tsd.ascanner.utils

object Gs1Parser {
    // Extracts EAN-13 from GS1 DataMatrix by finding AI(01) GTIN (14 digits), then dropping leading 0 if present
    fun extractEan13FromGs1(raw: String): String? {
        // Remove possible ASCII GS separator if present
        val cleaned = raw.replace("\u001D", "")
        val idx = cleaned.indexOf("01")
        if (idx == -1 || idx + 2 + 14 > cleaned.length) return null
        val gtin14 = cleaned.substring(idx + 2, idx + 2 + 14)
        if (!gtin14.all { it.isDigit() }) return null
        return if (gtin14.startsWith('0')) gtin14.drop(1) else gtin14
    }
}


