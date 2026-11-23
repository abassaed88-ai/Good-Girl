package com.goodgirl.accounting.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedBackupCodecTest {

    @Test
    fun pbkdf2MatchesKnownHmacSha256Vector() {
        val actual = EncryptedBackupCodec.deriveKeyForTesting(
            password = "password".toCharArray(),
            salt = "salt".toByteArray(Charsets.UTF_8),
            iterations = 1,
            keyLength = 32
        )
        try {
            assertEquals(
                "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17",
                actual.joinToString("") { "%02x".format(it) }
            )
        } finally {
            actual.fill(0)
        }
    }

    @Test
    fun encryptedBackupRoundTripsAndRejectsWrongPasswordsAndTampering() {
        val plaintext = """{"format":"good-girl-accounting-backup","records":3}"""
            .toByteArray(Charsets.UTF_8)
        val password = "GoodGirl-Backup-2026"
        val encrypted = EncryptedBackupCodec.encrypt(plaintext, password.toCharArray())

        assertTrue(EncryptedBackupCodec.isEncrypted(encrypted))
        assertFalse(EncryptedBackupCodec.isEncrypted(plaintext))
        assertArrayEquals(
            plaintext,
            EncryptedBackupCodec.decrypt(encrypted, password.toCharArray())
        )

        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.decrypt(encrypted, "incorrect".toCharArray())
        }

        val tampered = encrypted.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.decrypt(tampered, password.toCharArray())
        }
    }

    @Test
    fun encryptionRequiresANonShortPassword() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.encrypt(
                plaintext = byteArrayOf(1, 2, 3),
                password = "short".toCharArray()
            )
        }
    }
}
