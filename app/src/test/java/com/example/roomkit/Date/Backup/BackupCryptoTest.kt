package com.example.roomkit.Date.Backup

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    private val password="test-only password".toCharArray()
    @Test fun roundTripPreservesJsonBytes() {
        val bytes="{\"amountKhr\":9223372036854775807,\"tenant\":\"Demo\"}".toByteArray()
        assertArrayEquals(bytes,BackupCrypto.decrypt(BackupCrypto.encrypt(bytes,password),password))
    }
    @Test fun wrongPasswordIsRejected() {
        val value=BackupCrypto.encrypt("{}".toByteArray(),password)
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(value,"different password".toCharArray()) }
    }
    @Test fun ciphertextModificationIsRejected() {
        val value=BackupCrypto.encrypt("{}".toByteArray(),password)
        value.ciphertext[0]=(value.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { BackupCrypto.decrypt(value,password) }
    }
    @Test fun eachBackupUsesNewRandomness() {
        val a=BackupCrypto.encrypt("{}".toByteArray(),password);val b=BackupCrypto.encrypt("{}".toByteArray(),password)
        assertFalse(a.salt.contentEquals(b.salt));assertFalse(a.iv.contentEquals(b.iv))
    }
    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt(byteArrayOf(),"short".toCharArray()) }
    }
    @Test fun truncatedEnvelopeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(BackupCrypto.Envelope(ByteArray(16),ByteArray(12),ByteArray(3)),password) }
    }
}
