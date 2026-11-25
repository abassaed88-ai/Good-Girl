package com.goodgirl.accounting.domain

object CategoryInputRules {
    fun normalizeName(value: String): String {
        val name = value.trim().replace(Regex("\\s+"), " ")
        require(name.isNotEmpty()) { "اسم التصنيف مطلوب" }
        require(name.length <= 80) { "اسم التصنيف طويل جداً" }
        return name
    }
}
