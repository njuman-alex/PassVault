package com.c0dex.core.crypto

import java.security.SecureRandom

/**
 * Иерархия ключей PMV3 (FORMAT.md §3).
 *
 *   мастер --Argon2id--> KEK --AES-GCM wrap--> Wrap{type, iv, ct(DEK)}
 *   DEK (случайные 32 байта, один раз при создании) --AES-GCM--> записи
 *
 * Контракт:
 *  - DEK ровно 32 байта, генерится один раз на vault;
 *  - wrap шифруется KEK'ом с AAD = type (обёртку нельзя выдать за другой тип);
 *  - смена мастера = переобёртка одного блоба, записи не трогаются;
 *  - промежуточный DEK при ротации затирается;
 *  - ноль Android-импортов.
 */
object KeyHierarchy {

    const val DEK_SIZE_BYTES = 32
    const val WRAP_TYPE_MASTER = "master"

    private val random = SecureRandom()

    /** Обёртка DEK в заголовке контейнера. */
    data class Wrap(val type: String, val iv: ByteArray, val ct: ByteArray)

    /** Случайный DEK для нового vault. */
    fun newDek(): ByteArray = ByteArray(DEK_SIZE_BYTES).also(random::nextBytes)

    /** KEK из мастера и параметров заголовка. */
    fun deriveKek(master: CharArray, params: Argon2Kdf.Params): ByteArray =
        Argon2Kdf.derive(master, params)

    /** Оборачивает DEK ключом KEK. type绑定 через AAD. */
    fun wrapDek(dek: ByteArray, kek: ByteArray, type: String = WRAP_TYPE_MASTER): Wrap {
        require(dek.size == DEK_SIZE_BYTES) { "dek must be $DEK_SIZE_BYTES bytes" }
        require(kek.size == Argon2Kdf.KEY_SIZE_BYTES) { "kek must be ${Argon2Kdf.KEY_SIZE_BYTES} bytes" }
        require(type.isNotBlank()) { "wrap type must not be blank" }
        val enc = AesGcm.encrypt(kek, dek, type.toByteArray(Charsets.UTF_8))
        return Wrap(type, enc.iv, enc.ciphertext)
    }

    /** Разворачивает DEK. null при неверном KEK/типе/повреждении. */
    fun unwrapDek(wrap: Wrap, kek: ByteArray): ByteArray? {
        val dek = AesGcm.decrypt(kek, wrap.iv, wrap.ct, wrap.type.toByteArray(Charsets.UTF_8))
            ?: return null
        if (dek.size != DEK_SIZE_BYTES) return null
        return dek
    }

    /**
     * Ротация мастера: unwrap старым KEK -> wrap новым KEK.
     * Записи vault не трогаются. null если старый KEK неверен.
     */
    fun rotateMaster(wrap: Wrap, oldKek: ByteArray, newKek: ByteArray): Wrap? {
        val dek = unwrapDek(wrap, oldKek) ?: return null
        return try {
            wrapDek(dek, newKek, wrap.type)
        } finally {
            dek.fill(0)
        }
    }
}