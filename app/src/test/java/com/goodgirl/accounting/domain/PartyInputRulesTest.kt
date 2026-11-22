package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PartyInputRulesTest {
    @Test
    fun trimsPartyName() {
        assertEquals("أحمد", PartyInputRules.normalizeName("  أحمد "))
    }

    @Test
    fun rejectsEmptyPartyName() {
        assertThrows(IllegalArgumentException::class.java) {
            PartyInputRules.normalizeName(" ")
        }
    }

    @Test
    fun acceptsOnlyCustomerOrSupplier() {
        assertEquals("CUSTOMER", PartyInputRules.normalizeKind("customer"))
        assertEquals("SUPPLIER", PartyInputRules.normalizeKind("SUPPLIER"))
        assertThrows(IllegalArgumentException::class.java) {
            PartyInputRules.normalizeKind("OTHER")
        }
    }
}
