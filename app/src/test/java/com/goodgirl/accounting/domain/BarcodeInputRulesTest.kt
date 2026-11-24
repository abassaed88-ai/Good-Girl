package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeInputRulesTest {
    @Test
    fun acceptsAndTrimsValidEan13() {
        assertEquals("4006381333931", BarcodeInputRules.normalize(" 4006381333931 ", "EAN_13"))
    }

    @Test
    fun acceptsValidEan8AndUpcA() {
        assertEquals("96385074", BarcodeInputRules.normalize("96385074", "EAN_8"))
        assertEquals("036000291452", BarcodeInputRules.normalize("036000291452", "UPC_A"))
    }

    @Test
    fun rejectsInvalidCheckDigitsAndNonAsciiDigits() {
        assertThrows(IllegalArgumentException::class.java) {
            BarcodeInputRules.normalize("4006381333932", "EAN_13")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BarcodeInputRules.normalize("٤٠٠٦٣٨١٣٣٣٩٣١", "EAN_13")
        }
    }

    @Test
    fun createsInternalCodeInCode128CharacterSet() {
        val value = BarcodeInputRules.internalCode("123456789012")
        assertEquals("GG123456789012", value)
        assertEquals(value, BarcodeInputRules.normalize(value, "CODE_128"))
    }

    @Test
    fun rejectsUnsupportedAndMalformedFormats() {
        assertThrows(IllegalArgumentException::class.java) {
            BarcodeInputRules.normalize("12345", "EAN_13")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BarcodeInputRules.normalize("abc", "QR")
        }
    }

    @Test
    fun calculatesEanCheckDigit() {
        assertEquals('1', BarcodeInputRules.calculateCheckDigit("400638133393"))
        assertTrue(BarcodeInputRules.isValidEan("4006381333931", 13))
        assertFalse(BarcodeInputRules.isValidEan("4006381333932", 13))
    }
}
