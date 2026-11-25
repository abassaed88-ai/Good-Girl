package com.goodgirl.accounting.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveBytesTest {

    @Test
    fun useAndWipeClearsBytesAfterSuccessfulBlock() {
        val bytes = byteArrayOf(10, 20, 30)

        val total = bytes.useAndWipe { payload -> payload.sum() }

        assertEquals(60, total)
        assertArrayEquals(byteArrayOf(0, 0, 0), bytes)
    }

    @Test
    fun useAndWipeClearsBytesWhenBlockThrows() {
        val bytes = byteArrayOf(7, 8, 9)

        val error = runCatching {
            bytes.useAndWipe<Int> {
                throw IllegalStateException("expected failure")
            }
        }.exceptionOrNull()

        assertEquals("expected failure", error?.message)
        assertArrayEquals(byteArrayOf(0, 0, 0), bytes)
    }
}
