package com.goodgirl.accounting.domain

object PartyInputRules {
    fun normalizeName(value: String): String {
        val name = value.trim()
        require(name.isNotEmpty()) { "الاسم مطلوب" }
        require(name.length <= 120) { "الاسم طويل جداً" }
        return name
    }

    fun normalizeKind(value: String): String {
        val kind = value.trim().uppercase()
        require(kind == "CUSTOMER" || kind == "SUPPLIER") { "نوع جهة التعامل غير صحيح" }
        return kind
    }

    fun normalizeOpeningCurrency(value: String): String {
        val currency = value.trim().uppercase()
        require(currency == "SYP" || currency == "USD") { "عملة الرصيد الافتتاحي غير مدعومة" }
        return currency
    }
}
