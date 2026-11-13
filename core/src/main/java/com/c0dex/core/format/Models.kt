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
}

/** Зашифрованная запись: id открыт (UUID), iv+ct — шифр payload с AAD=id. */
data class RecordEnvelope(val id: String, val iv: ByteArray, val ct: ByteArray)

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