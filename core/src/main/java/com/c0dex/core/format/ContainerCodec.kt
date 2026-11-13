package com.c0dex.core.format

import com.c0dex.core.crypto.AesGcm
import com.c0dex.core.crypto.KeyHierarchy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Кодек контейнера PMV3 (FORMAT.md §4, §6).
 *
 * Контракт:
 *  - JSON-текст UTF-8/LF; base64 standard alphabet + padding, без переносов;
 *  - magic="PMV3", formatVersion=3, kdf.alg="argon2id";
 *  - парсер терпит неизвестные поля (forward-compat);
 *  - payload записи шифруется DEK с AAD=id (защита от перестановки);
 *  - ошибки формата -> IllegalArgumentException; ошибки шифра payload -> null.
 */
object ContainerCodec {

    const val MAGIC = "PMV3"
    const val VERSION = 3
    const val KDF_ALG = "argon2id"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val b64enc: Base64.Encoder = Base64.getEncoder()
    private val b64dec: Base64.Decoder = Base64.getDecoder()

    // ===== wire-модели (base64 строками) =====
    @Serializable private data class KdfJson(val alg: String, val memoryKiB: Int, val iterations: Int, val parallelism: Int, val salt: String)
    @Serializable private data class WrapJson(val type: String, val iv: String, val ct: String)
    @Serializable private data class EnvelopeJson(val id: String, val iv: String, val ct: String)
    @Serializable private data class ContainerJson(val magic: String, val formatVersion: Int, val kdf: KdfJson, val wraps: List<WrapJson>, val records: List<EnvelopeJson>)
    @Serializable private data class PayloadJson(val description: String, val password: String, val createdAt: Long, val updatedAt: Long, val deleted: Boolean = false)

    // ===== контейнер =====

    fun serialize(c: VaultContainer): String = json.encodeToString(
        ContainerJson(
            magic = MAGIC,
            formatVersion = VERSION,
            kdf = KdfJson(KDF_ALG, c.kdf.memoryKiB, c.kdf.iterations, c.kdf.parallelism, b64(c.kdf.salt)),
            wraps = c.wraps.map { WrapJson(it.type, b64(it.iv), b64(it.ct)) },
            records = c.records.map { EnvelopeJson(it.id, b64(it.iv), b64(it.ct)) }
        )
    )

    fun parse(text: String): VaultContainer {
        val w = json.decodeFromString(ContainerJson.serializer(), text)
        require(w.magic == MAGIC) { "bad magic: ${w.magic}" }
        require(w.formatVersion == VERSION) { "bad version: ${w.formatVersion}" }
        require(w.kdf.alg == KDF_ALG) { "bad kdf alg: ${w.kdf.alg}" }
        val salt = unb64(w.kdf.salt)
        require(salt.size == 16) { "bad salt size: ${salt.size}" }
        require(w.wraps.isNotEmpty()) { "no wraps" }
        val records = w.records.map { RecordEnvelope(it.id, unb64(it.iv), unb64(it.ct)) }
        require(records.map { it.id }.distinct().size == records.size) { "duplicate record ids" }
        return VaultContainer(
            kdf = KdfConfig(w.kdf.memoryKiB, w.kdf.iterations, w.kdf.parallelism, salt),
            wraps = w.wraps.map { KeyHierarchy.Wrap(it.type, unb64(it.iv), unb64(it.ct)) },
            records = records
        )
    }

    // ===== payload записи =====

    fun sealPayload(dek: ByteArray, id: String, payload: RecordPayload): RecordEnvelope {
        val pt = json.encodeToString(
            PayloadJson(payload.description, payload.password, payload.createdAt, payload.updatedAt, payload.deleted)
        ).toByteArray(Charsets.UTF_8)
        val enc = AesGcm.encrypt(dek, pt, id.toByteArray(Charsets.UTF_8))
        return RecordEnvelope(id, enc.iv, enc.ciphertext)
    }

    fun openPayload(dek: ByteArray, env: RecordEnvelope): RecordPayload? {
        val pt = AesGcm.decrypt(dek, env.iv, env.ct, env.id.toByteArray(Charsets.UTF_8)) ?: return null
        return try {
            val p = json.decodeFromString(PayloadJson.serializer(), String(pt, Charsets.UTF_8))
            RecordPayload(p.description, p.password, p.createdAt, p.updatedAt, p.deleted)
        } catch (e: Exception) {
            null
        }
    }

    private fun b64(b: ByteArray): String = b64enc.encodeToString(b)
    private fun unb64(s: String): ByteArray = b64dec.decode(s)
}