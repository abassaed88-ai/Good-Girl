package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.domain.PartyInputRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PartyRepository(private val database: AccountingDatabase) {
    fun observeParties(kind: String): Flow<List<PartyEntity>> {
        val cleanKind = PartyInputRules.normalizeKind(kind)
        return database.partyDao().observeByKind(cleanKind)
    }

    suspend fun saveParty(
        id: String?,
        kind: String,
        name: String,
        phone: String,
        address: String,
        note: String
    ) {
        val cleanKind = PartyInputRules.normalizeKind(kind)
        val cleanName = PartyInputRules.normalizeName(name)
        val now = System.currentTimeMillis()
        database.withTransaction {
            if (id == null) {
                database.partyDao().insert(
                    PartyEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        kind = cleanKind,
                        phone = phone.trim(),
                        address = address.trim(),
                        openingBalance = "0",
                        currencyCode = "SYP",
                        note = note.trim(),
                        createdAt = now
                    )
                )
            } else {
                val current = database.partyDao().findById(id)
                    ?: throw IllegalArgumentException("جهة التعامل غير موجودة")
                require(current.kind == cleanKind) { "نوع جهة التعامل غير مطابق" }
                database.partyDao().update(
                    current.copy(
                        name = cleanName,
                        phone = phone.trim(),
                        address = address.trim(),
                        note = note.trim()
                    )
                )
            }
        }
    }
}
