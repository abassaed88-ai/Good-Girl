package com.goodgirl.accounting.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable password-based backup envelope.
 *
 * Format: magic(8) | version(1) | PBKDF2 iterations(4) | salt(16) |
 * GCM nonce(12) | AES-GCM ciphertext + 128-bit authentication tag.
 * The complete header is authenticated as AES-GCM additional data.
 */
object EncryptedBackupCodec {
    const val MIN_PASSWORD_LENGTH = 8
    const val ENVELOPE_OVERHEAD = 57

    private const val VERSION = 1
    private const val ITERATIONS = 600_000
    private const val MIN_RESTORE_ITERATIONS = 600_000
    private const val MAX_RESTORE_ITERATIONS = 1_200_000
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 8 + 1 + 4 + SALT_BYTES + NONCE_BYTES
    private val MAGIC = "GGBACKUP".toByteArray(StandardCharsets.US_ASCII)

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    /**
     * Encrypts a backup and consumes (zeroes) the supplied password array.
     */
    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        try {
            require(password.size >= MIN_PASSWORD_LENGTH) {
                "كلمة مرور النسخة يجب ألا تقل عن 8 أحرف"
            }
            require(plaintext.isNotEmpty()) { "لا يمكن تشفير ملف نسخة احتياطية فارغ" }

            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
            val header = ByteBuffer.allocate(HEADER_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .put(MAGIC)
                .put(VERSION.toByte())
                .putInt(ITERATIONS)
                .put(salt)
                .put(nonce)
                .array()

            val keyBytes = pbkdf2HmacSha256(password, salt, ITERATIONS, KEY_BYTES)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(keyBytes, "AES"),
                    GCMParameterSpec(TAG_BITS, nonce)
                )
                cipher.updateAAD(header)
                return header + cipher.doFinal(plaintext)
            } finally {
                keyBytes.fill(0)
                salt.fill(0)
                nonce.fill(0)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Decrypts an encrypted backup and consumes (zeroes) the supplied password.
     * Wrong passwords and file tampering are reported as a single safe error.
     */
    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        try {
            require(isEncrypted(envelope)) { "صيغة النسخة المشفّرة غير معروفة" }
            require(password.isNotEmpty()) { "أدخل كلمة مرور النسخة الاحتياطية" }
            require(envelope.size >= HEADER_BYTES + TAG_BITS / 8) {
                "ملف النسخة الاحتياطية المشفّر ناقص أو تالف"
            }

            val header = envelope.copyOfRange(0, HEADER_BYTES)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC.size)
            buffer.get(magic)
            require(magic.contentEquals(MAGIC)) { "ملف النسخة الاحتياطية غير صالح" }

            val version = buffer.get().toInt() and 0xff
            require(version == VERSION) { "إصدار تشفير النسخة الاحتياطية غير مدعوم" }

            val iterations = buffer.int
            require(iterations in MIN_RESTORE_ITERATIONS..MAX_RESTORE_ITERATIONS) {
                "إعدادات تشفير النسخة الاحتياطية غير صالحة"
            }

            val salt = ByteArray(SALT_BYTES).also(buffer::get)
            val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
            val keyBytes = pbkdf2HmacSha256(password, salt, iterations, KEY_BYTES)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(keyBytes, "AES"),
                    GCMParameterSpec(TAG_BITS, nonce)
                )
                cipher.updateAAD(header)
                return cipher.doFinal(
                    envelope,
                    HEADER_BYTES,
                    envelope.size - HEADER_BYTES
                )
            } catch (error: AEADBadTagException) {
                throw IllegalArgumentException(
                    "كلمة المرور غير صحيحة أو ملف النسخة الاحتياطية تعرّض للتعديل",
                    error
                )
            } catch (error: GeneralSecurityException) {
                throw IllegalArgumentException("تعذّر فك تشفير النسخة الاحتياطية", error)
            } finally {
                keyBytes.fill(0)
                salt.fill(0)
                nonce.fill(0)
                header.fill(0)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * PBKDF2-HMAC-SHA256 implementation using Mac (available on Android API 1+).
     * This avoids PBEWithHmacSHA256 provider availability restrictions on API 24.
     */
    internal fun deriveKeyForTesting(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray = pbkdf2HmacSha256(password, salt, iterations, keyLength)

    private fun pbkdf2HmacSha256(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        require(iterations > 0) { "عدد دورات الاشتقاق غير صالح" }
        require(keyLength > 0) { "طول المفتاح غير صالح" }

        val passwordBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(passwordBuffer.remaining()).also(passwordBuffer::get)
        val mac = Mac.getInstance("HmacSHA256")
        val output = ByteArray(keyLength)
        val saltAndBlock = ByteArray(salt.size + 4)
        salt.copyInto(saltAndBlock)

        try {
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            val hashLength = mac.macLength
            val blockCount = (keyLength + hashLength - 1) / hashLength
            var outputOffset = 0

            for (blockIndex in 1..blockCount) {
                val counterOffset = salt.size
                saltAndBlock[counterOffset] = (blockIndex ushr 24).toByte()
                saltAndBlock[counterOffset + 1] = (blockIndex ushr 16).toByte()
                saltAndBlock[counterOffset + 2] = (blockIndex ushr 8).toByte()
                saltAndBlock[counterOffset + 3] = blockIndex.toByte()

                var u = mac.doFinal(saltAndBlock)
                val accumulator = u.copyOf()
                for (round in 1 until iterations) {
                    val next = mac.doFinal(u)
                    u.fill(0)
                    u = next
                    for (index in accumulator.indices) {
                        accumulator[index] = (
                            accumulator[index].toInt() xor u[index].toInt()
                        ).toByte()
                    }
                }

                val bytesToCopy = minOf(hashLength, keyLength - outputOffset)
                accumulator.copyInto(output, outputOffset, 0, bytesToCopy)
                outputOffset += bytesToCopy
                u.fill(0)
                accumulator.fill(0)
            }
            return output
        } catch (error: Exception) {
            output.fill(0)
            throw error
        } finally {
            mac.reset()
            passwordBytes.fill(0)
            saltAndBlock.fill(0)
            if (passwordBuffer.hasArray()) {
                passwordBuffer.array().fill(0)
            }
        }
    }
}
