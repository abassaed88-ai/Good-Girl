package com.goodgirl.accounting.domain

data class NormalizedAccountInput(
    val code: String,
    val name: String,
    val accountType: String,
    val normalSide: String
)

/** Validates and normalizes user-created chart-of-accounts records. */
object AccountInputRules {
    private val allowedTypes = setOf("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE")

    fun normalize(code: String, name: String, accountType: String): NormalizedAccountInput {
        val cleanCode = code.trim()
        val cleanName = name.trim()
        val cleanType = accountType.trim().uppercase()

        require(cleanCode.matches(Regex("^[0-9]{3,10}$"))) {
            "رمز الحساب يجب أن يتكون من 3 إلى 10 أرقام إنكليزية"
        }
        require(cleanName.length in 2..80) {
            "اسم الحساب يجب أن يتكون من حرفين إلى 80 حرفاً"
        }
        require(cleanType in allowedTypes) {
            "نوع الحساب المحدد غير مدعوم"
        }

        val normalSide = if (cleanType == "ASSET" || cleanType == "EXPENSE") "DEBIT" else "CREDIT"
        return NormalizedAccountInput(
            code = cleanCode,
            name = cleanName,
            accountType = cleanType,
            normalSide = normalSide
        )
    }
}
