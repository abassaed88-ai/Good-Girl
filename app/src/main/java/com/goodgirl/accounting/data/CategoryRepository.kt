package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.CategoryEntity
import com.goodgirl.accounting.domain.CategoryInputRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CategoryRepository(private val database: AccountingDatabase) {
    fun observeCategories(): Flow<List<CategoryEntity>> =
        database.categoryDao().observeAll()

    suspend fun saveCategory(id: String?, name: String, note: String) {
        val cleanName = CategoryInputRules.normalizeName(name)
        database.withTransaction {
            val existingWithName = database.categoryDao().findByName(cleanName)
            if (id == null) {
                require(existingWithName == null) { "يوجد تصنيف بهذا الاسم بالفعل" }
                database.categoryDao().insert(
                    CategoryEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        note = note.trim(),
                        createdAt = System.currentTimeMillis()
                    )
                )
            } else {
                val current = database.categoryDao().findById(id)
                    ?: throw IllegalArgumentException("التصنيف غير موجود")
                require(existingWithName == null || existingWithName.id == id) {
                    "يوجد تصنيف آخر بهذا الاسم بالفعل"
                }
                database.categoryDao().update(current.copy(name = cleanName, note = note.trim()))
            }
        }
    }
}
