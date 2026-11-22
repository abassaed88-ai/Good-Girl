package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoryInputRulesTest {
    @Test
    fun trimsAndCollapsesSpaces() {
        assertEquals("العناية بالبشرة", CategoryInputRules.normalizeName(" العناية   بالبشرة "))
    }

    @Test
    fun rejectsEmptyCategoryName() {
        assertThrows(IllegalArgumentException::class.java) {
            CategoryInputRules.normalizeName("   ")
        }
    }

    @Test
    fun rejectsVeryLongCategoryName() {
        assertThrows(IllegalArgumentException::class.java) {
            CategoryInputRules.normalizeName("ا".repeat(81))
        }
    }
}
