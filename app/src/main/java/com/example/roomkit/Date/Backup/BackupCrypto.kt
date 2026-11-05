package com.example.roomkit.Date.Backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Platform-independent authenticated encryption; passwords are never stored. */
object BackupCrypto {
    const val ITERATIONS = 210_000
    const val MAX_BYTES = 20 * 1024 * 1024
    data class Envelope(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)
    fun encrypt(plain: ByteArray, password: CharArray): Envelope {
        require(password.size >= 10) { "Use a password of at least 10 characters." }
        require(plain.size <= MAX_BYTES) { "Backup exceeds the 20 MiB limit." }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        return Envelope(salt, iv, crypt(Cipher.ENCRYPT_MODE, plain, password, salt, iv))
    }
    fun decrypt(value: Envelope, password: CharArray): ByteArray {
        require(value.salt.size == 16 && value.iv.size == 12 && value.ciphertext.size in 16..MAX_BYTES + 16) {
            "Invalid backup encryption data."
        }
        return crypt(Cipher.DECRYPT_MODE, value.ciphertext, password, value.salt, value.iv)
    }
    private fun crypt(mode: Int, bytes: ByteArray, password: CharArray, salt: ByteArray, iv: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val raw = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD("RoomKit JSON backup v1 schema11".toByteArray(Charsets.UTF_8))
            cipher.doFinal(bytes)
        } finally { raw.fill(0) }
    }
}
