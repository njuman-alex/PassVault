package com.c0dex.core.format

import com.c0dex.core.crypto.Argon2Kdf
import com.c0dex.core.crypto.KeyHierarchy

/**
 * Доменные модели контейнера PMV3 (FORMAT.md §4).
 * Байты живут в памяти; base64 — только «на проводе» (в JSON-файле).
 */

data class KdfConfig(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: ByteArray
) {
    fun toParams(): Argon2Kdf.Params = Argon2Kdf.Params(memoryKiB, iterations, parallelism, salt)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KdfConfig) return false
        return memoryKiB == other.memoryKiB &&
                iterations == other.iterations &&
                parallelism == other.parallelism &&
                salt.contentEquals(other.salt)
    }

    override fun hashCode(): Int {
        var result = memoryKiB
        result = 31 * result + iterations
        result = 31 * result + parallelism
        result = 31 * result + salt.contentHashCode()
        return result
    }
}

/** Зашифрованная запись: id открыт (UUID), iv+ct — шифр payload с AAD=id. */
data class RecordEnvelope(val id: String, val iv: ByteArray, val ct: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordEnvelope) return false
        return id == other.id &&
                iv.contentEquals(other.iv) &&
                ct.contentEquals(other.ct)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ct.contentHashCode()
        return result
    }
}

data class VaultContainer(
    val kdf: KdfConfig,
    val wraps: List<KeyHierarchy.Wrap>,
    val records: List<RecordEnvelope>
)

/** Расшифрованное содержимое записи (внутри ct). */
data class RecordPayload(
    val description: String,
    val password: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false
)