package com.goodgirl.accounting.data

/** Runs a block with sensitive bytes and clears the same array on every exit path. */
internal inline fun <T> ByteArray.useAndWipe(block: (ByteArray) -> T): T {
    return try {
        block(this)
    } finally {
        fill(0)
    }
}
