package com.goodgirl.accounting.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSearchRulesTest {
    @Test
    fun blankQueryMatchesEveryProductInSelectedCategory() {
        assertTrue(ProductSearchRules.matches("زيت عطري", "P-001", "cat-1", " ", "cat-1"))
        assertFalse(ProductSearchRules.matches("صبغة شعر", "P-002", "cat-2", "", "cat-1"))
    }

    @Test
    fun queryMatchesProductNameIgnoringCase() {
        assertTrue(ProductSearchRules.matches("Hair Spray", "A-10", null, "spray", null))
    }

    @Test
    fun queryMatchesSku() {
        assertTrue(ProductSearchRules.matches("زيت عطري", "GG-245", null, "gg-24", null))
    }

    @Test
    fun categoryAndTextFiltersMustBothMatch() {
        assertFalse(ProductSearchRules.matches("زيت عطري", "GG-1", "cat-1", "صبغة", "cat-1"))
        assertFalse(ProductSearchRules.matches("زيت عطري", "GG-1", "cat-2", "زيت", "cat-1"))
        assertTrue(ProductSearchRules.matches("زيت عطري", "GG-1", "cat-1", "زيت", "cat-1"))
    }
}
