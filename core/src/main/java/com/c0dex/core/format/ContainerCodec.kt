package com.c0dex.core.format

import com.c0dex.core.crypto.AesGcm
import com.c0dex.core.crypto.Argon2Kdf
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

    // Защитные лимиты формата. Это не лимиты мастер-пароля.
    //private const val MAX_CONTAINER_CHARS = 64 * 1024 * 1024
    const val MAX_CONTAINER_CHARS = 64 * 1024 * 1024

    // D-029: байтовый кап для платформенного чтения (UTF-8 worst case = 4 байта/символ)
    const val MAX_CONTAINER_BYTES = MAX_CONTAINER_CHARS * 4
    private const val MAX_WRAPS = 8
    private const val MAX_RECORDS = 100_000
    private const val MAX_ID_CHARS = 256
    private const val MAX_WRAP_TYPE_CHARS = 64
    private const val MAX_RECORD_CIPHERTEXT_BYTES = 4 * 1024 * 1024

    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BYTES = 16

    private const val MASTER_WRAP_CIPHERTEXT_BYTES =
        KeyHierarchy.DEK_SIZE_BYTES + GCM_TAG_BYTES

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val b64enc: Base64.Encoder = Base64.getEncoder()
    private val b64dec: Base64.Decoder = Base64.getDecoder()

    // ===== wire-модели (base64 строками) =====

    @Serializable
    private data class KdfJson(
        val alg: String,
        val memoryKiB: Int,
        val iterations: Int,
        val parallelism: Int,
        val salt: String
    )

    @Serializable
    private data class WrapJson(
        val type: String,
        val iv: String,
        val ct: String
    )

    @Serializable
    private data class EnvelopeJson(
        val id: String,
        val iv: String,
        val ct: String
    )

    @Serializable
    private data class ContainerJson(
        val magic: String,
        val formatVersion: Int,
        val kdf: KdfJson,
        val wraps: List<WrapJson>,
        val records: List<EnvelopeJson>
    )

    @Serializable
    private data class PayloadJson(
        val description: String,
        val password: String,
        val createdAt: Long,
        val updatedAt: Long,
        val deleted: Boolean = false,
        val login: String = ""
    )

    // ===== проверки модели перед записью =====

    private fun validateKdf(
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        salt: ByteArray
    ) {
        require(salt.size == Argon2Kdf.SALT_SIZE_BYTES) {
            "bad salt size: ${salt.size}"
        }

        require(
            memoryKiB > 0 &&
                    memoryKiB <= Argon2Kdf.MAX_MEMORY_KIB
        ) {
            "bad Argon2 memory: $memoryKiB"
        }

        require(
            iterations >= 1 &&
                    iterations <= Argon2Kdf.MAX_ITERATIONS
        ) {
            "bad Argon2 iterations: $iterations"
        }

        require(
            parallelism >= 1 &&
                    parallelism <= Argon2Kdf.MAX_PARALLELISM
        ) {
            "bad Argon2 parallelism: $parallelism"
        }

        require(memoryKiB.toLong() >= 8L * parallelism) {
            "Argon2 memory is too small for parallelism"
        }
    }

    private fun validateWraps(wraps: List<KeyHierarchy.Wrap>) {
        require(wraps.isNotEmpty()) {
            "no wraps"
        }

        require(wraps.size <= MAX_WRAPS) {
            "too many wraps"
        }

        val masterWrapCount = wraps.count {
            it.type == KeyHierarchy.WRAP_TYPE_MASTER
        }

        require(masterWrapCount == 1) {
            "container must contain exactly one master wrap"
        }

        wraps.forEach { wrap ->
            require(wrap.type.isNotBlank()) {
                "empty wrap type"
            }

            require(wrap.type.length <= MAX_WRAP_TYPE_CHARS) {
                "wrap type is too long"
            }

            require(wrap.iv.size == GCM_IV_BYTES) {
                "bad wrap iv size: ${wrap.iv.size}"
            }

            require(wrap.ct.size >= GCM_TAG_BYTES) {
                "wrap ciphertext is shorter than GCM tag"
            }

            require(wrap.ct.size <= MAX_RECORD_CIPHERTEXT_BYTES) {
                "wrap ciphertext is too large"
            }

            if (wrap.type == KeyHierarchy.WRAP_TYPE_MASTER) {
                require(wrap.ct.size == MASTER_WRAP_CIPHERTEXT_BYTES) {
                    "bad master wrap ciphertext size: ${wrap.ct.size}"
                }
            }
        }
    }

    private fun validateRecords(records: List<RecordEnvelope>) {
        require(records.size <= MAX_RECORDS) {
            "too many records"
        }

        records.forEach { record ->
            require(record.id.isNotBlank()) {
                "empty record id"
            }

            require(record.id.length <= MAX_ID_CHARS) {
                "record id is too long"
            }

            require(record.iv.size == GCM_IV_BYTES) {
                "bad record iv size: ${record.iv.size}"
            }

            require(record.ct.size >= GCM_TAG_BYTES) {
                "record ciphertext is shorter than GCM tag"
            }

            require(record.ct.size <= MAX_RECORD_CIPHERTEXT_BYTES) {
                "record ciphertext is too large"
            }
        }

        require(records.map { it.id }.distinct().size == records.size) {
            "duplicate record ids"
        }
    }

    private fun validateContainer(c: VaultContainer) {
        validateKdf(
            c.kdf.memoryKiB,
            c.kdf.iterations,
            c.kdf.parallelism,
            c.kdf.salt
        )

        validateWraps(c.wraps)
        validateRecords(c.records)
    }

    // ===== контейнер =====

    fun serialize(c: VaultContainer): String {
        // Проверяем модель до Base64-кодирования и формирования JSON.
        validateContainer(c)

        val result = json.encodeToString(
            ContainerJson(
                magic = MAGIC,
                formatVersion = VERSION,
                kdf = KdfJson(
                    KDF_ALG,
                    c.kdf.memoryKiB,
                    c.kdf.iterations,
                    c.kdf.parallelism,
                    b64(c.kdf.salt)
                ),
                wraps = c.wraps.map {
                    WrapJson(it.type, b64(it.iv), b64(it.ct))
                },
                records = c.records.map {
                    EnvelopeJson(it.id, b64(it.iv), b64(it.ct))
                }
            )
        )

        require(result.length <= MAX_CONTAINER_CHARS) {
            "container is too large"
        }

        return result
    }

    /*
    // СТАРАЯ ВЕРСИЯ serialize() — без проверки модели и размера результата.

    fun serialize(c: VaultContainer): String = json.encodeToString(
        ContainerJson(
            magic = MAGIC,
            formatVersion = VERSION,
            kdf = KdfJson(
                KDF_ALG,
                c.kdf.memoryKiB,
                c.kdf.iterations,
                c.kdf.parallelism,
                b64(c.kdf.salt)
            ),
            wraps = c.wraps.map {
                WrapJson(it.type, b64(it.iv), b64(it.ct))
            },
            records = c.records.map {
                EnvelopeJson(it.id, b64(it.iv), b64(it.ct))
            }
        )
    )
    */

    fun parse(text: String): VaultContainer {
        // Нельзя отдавать JSON-декодеру неограниченный вход.
        require(text.length <= MAX_CONTAINER_CHARS) {
            "container is too large"
        }

        val w = json.decodeFromString(
            ContainerJson.serializer(),
            text
        )

        require(w.magic == MAGIC) {
            "bad magic: ${w.magic}"
        }

        require(w.formatVersion == VERSION) {
            "bad version: ${w.formatVersion}"
        }

        require(w.kdf.alg == KDF_ALG) {
            "bad kdf alg: ${w.kdf.alg}"
        }

        // Соль Argon2.
        val salt = decodeBase64(
            w.kdf.salt,
            Argon2Kdf.SALT_SIZE_BYTES,
            "salt"
        )

        require(salt.size == Argon2Kdf.SALT_SIZE_BYTES) {
            "bad salt size: ${salt.size}"
        }

        // Параметры Argon2.
        validateKdf(
            w.kdf.memoryKiB,
            w.kdf.iterations,
            w.kdf.parallelism,
            salt
        )

        // Обёртки.
        require(w.wraps.isNotEmpty()) {
            "no wraps"
        }

        require(w.wraps.size <= MAX_WRAPS) {
            "too many wraps"
        }

        val masterWrapCount = w.wraps.count {
            it.type == KeyHierarchy.WRAP_TYPE_MASTER
        }

        require(masterWrapCount == 1) {
            "container must contain exactly one master wrap"
        }

        val wraps = w.wraps.map { item ->

            require(item.type.isNotBlank()) {
                "empty wrap type"
            }

            require(item.type.length <= MAX_WRAP_TYPE_CHARS) {
                "wrap type is too long"
            }

            val iv = decodeBase64(
                item.iv,
                GCM_IV_BYTES,
                "wrap iv"
            )

            require(iv.size == GCM_IV_BYTES) {
                "bad wrap iv size: ${iv.size}"
            }

            val ct = decodeBase64(
                item.ct,
                MAX_RECORD_CIPHERTEXT_BYTES,
                "wrap ciphertext"
            )

            require(ct.size >= GCM_TAG_BYTES) {
                "wrap ciphertext is shorter than GCM tag"
            }

            // Для master-wrap plaintext — DEK размером 32 байта,
            // плюс 16 байт GCM authentication tag.
            if (item.type == KeyHierarchy.WRAP_TYPE_MASTER) {
                require(ct.size == MASTER_WRAP_CIPHERTEXT_BYTES) {
                    "bad master wrap ciphertext size: ${ct.size}"
                }
            }

            KeyHierarchy.Wrap(item.type, iv, ct)
        }

        // Записи.
        require(w.records.size <= MAX_RECORDS) {
            "too many records"
        }

        val records = w.records.map { item ->

            require(item.id.isNotBlank()) {
                "empty record id"
            }

            require(item.id.length <= MAX_ID_CHARS) {
                "record id is too long"
            }

            val iv = decodeBase64(
                item.iv,
                GCM_IV_BYTES,
                "record iv"
            )

            require(iv.size == GCM_IV_BYTES) {
                "bad record iv size: ${iv.size}"
            }

            val ct = decodeBase64(
                item.ct,
                MAX_RECORD_CIPHERTEXT_BYTES,
                "record ciphertext"
            )

            require(ct.size >= GCM_TAG_BYTES) {
                "record ciphertext is shorter than GCM tag"
            }

            RecordEnvelope(item.id, iv, ct)
        }

        require(records.map { it.id }.distinct().size == records.size) {
            "duplicate record ids"
        }

        return VaultContainer(
            kdf = KdfConfig(
                w.kdf.memoryKiB,
                w.kdf.iterations,
                w.kdf.parallelism,
                salt
            ),
            wraps = wraps,
            records = records
        )
    }

    /*
    // СТАРАЯ ВЕРСИЯ parse() — оставлена для сравнения.

    fun parse(text: String): VaultContainer {
        val w = json.decodeFromString(ContainerJson.serializer(), text)

        require(w.magic == MAGIC) { "bad magic: ${w.magic}" }
        require(w.formatVersion == VERSION) { "bad version: ${w.formatVersion}" }
        require(w.kdf.alg == KDF_ALG) { "bad kdf alg: ${w.kdf.alg}" }

        val salt = unb64(w.kdf.salt)
        require(salt.size == 16) { "bad salt size: ${salt.size}" }

        require(w.wraps.isNotEmpty()) { "no wraps" }

        val records = w.records.map {
            RecordEnvelope(it.id, unb64(it.iv), unb64(it.ct))
        }

        require(records.map { it.id }.distinct().size == records.size) {
            "duplicate record ids"
        }

        return VaultContainer(
            kdf = KdfConfig(
                w.kdf.memoryKiB,
                w.kdf.iterations,
                w.kdf.parallelism,
                salt
            ),
            wraps = w.wraps.map {
                KeyHierarchy.Wrap(it.type, unb64(it.iv), unb64(it.ct))
            },
            records = records
        )
    }
    */

    // ===== payload записи =====

    fun sealPayload(
        dek: ByteArray,
        id: String,
        payload: RecordPayload
    ): RecordEnvelope {

        require(id.isNotBlank()) {
            "record id must not be blank"
        }

        require(id.length <= MAX_ID_CHARS) {
            "record id is too long"
        }

        val pt = json.encodeToString(
            PayloadJson(
                payload.description,
                payload.password,
                payload.createdAt,
                payload.updatedAt,
                payload.deleted,
                payload.login
            )
        ).toByteArray(Charsets.UTF_8)

        return try {
            // GCM добавляет authentication tag к plaintext.
            // Не допускаем создания ciphertext, который parse() отвергнет.
            require(
                pt.size.toLong() <=
                        MAX_RECORD_CIPHERTEXT_BYTES.toLong() - GCM_TAG_BYTES
            ) {
                "record payload is too large"
            }

            val enc = AesGcm.encrypt(
                dek,
                pt,
                id.toByteArray(Charsets.UTF_8)
            )

            RecordEnvelope(id, enc.iv, enc.ciphertext)

        } finally {
            // Убираем временный plaintext даже при ошибке шифрования.
            pt.fill(0)
        }
    }

    fun openPayload(
        dek: ByteArray,
        env: RecordEnvelope
    ): RecordPayload? {

        // Защита и от некорректных объектов, созданных не через parse().
        if (env.id.isBlank() || env.id.length > MAX_ID_CHARS) {
            return null
        }

        if (env.iv.size != GCM_IV_BYTES) {
            return null
        }

        if (env.ct.size < GCM_TAG_BYTES ||
            env.ct.size > MAX_RECORD_CIPHERTEXT_BYTES
        ) {
            return null
        }

        val pt = AesGcm.decrypt(
            dek,
            env.iv,
            env.ct,
            env.id.toByteArray(Charsets.UTF_8)
        ) ?: return null

        return try {
            val p = json.decodeFromString(
                PayloadJson.serializer(),
                String(pt, Charsets.UTF_8)
            )

            RecordPayload(
                p.description,
                p.password,
                p.createdAt,
                p.updatedAt,
                p.deleted,
                p.login
            )

        } catch (e: Exception) {
            null

        } finally {
            // ByteArray очищаем и при успешном разборе, и при исключении.
            pt.fill(0)
        }
    }

    /*
    // СТАРАЯ ВЕРСИЯ sealPayload() — plaintext не очищался.

    fun sealPayload(
        dek: ByteArray,
        id: String,
        payload: RecordPayload
    ): RecordEnvelope {
        val pt = json.encodeToString(
            PayloadJson(
                payload.description,
                payload.password,
                payload.createdAt,
                payload.updatedAt,
                payload.deleted,
                payload.login
            )
        ).toByteArray(Charsets.UTF_8)

        val enc = AesGcm.encrypt(
            dek,
            pt,
            id.toByteArray(Charsets.UTF_8)
        )

        return RecordEnvelope(id, enc.iv, enc.ciphertext)
    }

    // СТАРАЯ ВЕРСИЯ openPayload() — plaintext не очищался.

    fun openPayload(dek: ByteArray, env: RecordEnvelope): RecordPayload? {
        val pt = AesGcm.decrypt(
            dek,
            env.iv,
            env.ct,
            env.id.toByteArray(Charsets.UTF_8)
        ) ?: return null

        return try {
            val p = json.decodeFromString(
                PayloadJson.serializer(),
                String(pt, Charsets.UTF_8)
            )

            RecordPayload(
                p.description,
                p.password,
                p.createdAt,
                p.updatedAt,
                p.deleted,
                p.login
            )

        } catch (e: Exception) {
            null
        }
    }
    */

    // ===== Base64 =====

    private fun b64(b: ByteArray): String =
        b64enc.encodeToString(b)

    // было (D-028 заменил на decodeBase64, больше не используется):
    // private fun unb64(s: String): ByteArray = b64dec.decode(s)

    /**
     * Проверяет длину Base64 ДО декодирования, чтобы не выделять
     * чрезмерный ByteArray из заведомо огромной строки.
     */
    private fun decodeBase64(
        encoded: String,
        maxDecodedBytes: Int,
        fieldName: String
    ): ByteArray {

        val maxEncodedChars =
            ((maxDecodedBytes.toLong() + 2L) / 3L) * 4L

        require(encoded.length.toLong() <= maxEncodedChars) {
            "$fieldName is too large"
        }

        val decoded = try {
            b64dec.decode(encoded)

        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "invalid Base64 in $fieldName",
                e
            )
        }

        require(decoded.size <= maxDecodedBytes) {
            "$fieldName is too large"
        }

        return decoded
    }
}