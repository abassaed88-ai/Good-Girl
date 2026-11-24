package com.goodgirl.accounting.domain

object BarcodeInputRules {
    fun normalize(value: String, format: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "رقم الباركود مطلوب" }
        when (format) {
            "EAN_13" -> require(isValidEan(normalized, 13)) { "باركود EAN-13 غير صالح" }
            "EAN_8" -> require(isValidEan(normalized, 8)) { "باركود EAN-8 غير صالح" }
            "UPC_A" -> require(isValidEan(normalized, 12)) { "باركود UPC-A غير صالح" }
            "CODE_128" -> require(
                normalized.length <= 80 && normalized.all { it.code in 32..126 }
            ) { "باركود Code 128 يجب أن يحتوي على أحرف ASCII قابلة للطباعة وبحد أقصى 80 محرفاً" }
            else -> throw IllegalArgumentException("صيغة الباركود غير مدعومة")
        }
        return normalized
    }

    fun isValidEan(value: String, expectedLength: Int): Boolean {
        if (value.length != expectedLength || value.any { !it.isDigit() }) return false
        val body = value.dropLast(1)
        val expectedCheckDigit = calculateCheckDigit(body)
        return value.last() == expectedCheckDigit
    }

    fun calculateCheckDigit(body: String): Char {
        require(body.isNotEmpty() && body.all { it in '0'..'9' }) {
            "يجب أن يتكون جسم الباركود من أرقام فقط"
        }
        val weightedSum = body.reversed().mapIndexed { index, char ->
            char.digitToInt() * if (index % 2 == 0) 3 else 1
        }.sum()
        return ('0'.code + ((10 - weightedSum % 10) % 10)).toChar()
    }

    fun internalCode(suffix: String): String {
        require(suffix.length == 12 && suffix.all { it in '0'..'9' }) {
            "الرقم الداخلي يجب أن يتكون من 12 رقماً"
        }
        return "GG$suffix"
    }
}
