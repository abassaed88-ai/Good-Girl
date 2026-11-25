package com.goodgirl.accounting.domain

/**
 * Shared product matching rules for quick selection in sales and inventory screens.
 */
object ProductSearchRules {
    fun matches(
        name: String,
        sku: String,
        productCategoryId: String?,
        query: String,
        selectedCategoryId: String?
    ): Boolean {
        val normalizedQuery = query.trim()
        val matchesText = normalizedQuery.isEmpty() ||
            name.contains(normalizedQuery, ignoreCase = true) ||
            sku.contains(normalizedQuery, ignoreCase = true)
        val matchesCategory = selectedCategoryId == null ||
            productCategoryId == selectedCategoryId
        return matchesText && matchesCategory
    }
}
