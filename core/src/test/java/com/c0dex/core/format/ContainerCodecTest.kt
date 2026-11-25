package com.c0dex.core.format

import com.c0dex.core.crypto.Argon2Kdf
import com.c0dex.core.crypto.KeyHierarchy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerCodecTest {

    private val dek = KeyHierarchy.newDek()

    private val salt = ByteArray(16) { it.toByte() }

    private val kdf = KdfConfig(
        memoryKiB = 8192,
        iterations = 1,
        parallelism = 1,
        salt = salt
    )

    private val wrap = KeyHierarchy.Wrap(
        KeyHierarchy.WRAP_TYPE_MASTER,
        ByteArray(12) { it.toByte() },
        ByteArray(48) { it.toByte() }
    )

    private fun container(
        records: List<RecordEnvelope> = emptyList(),
        config: KdfConfig = kdf,
        wraps: List<KeyHierarchy.Wrap> = listOf(wrap)
    ) = VaultContainer(config, wraps, records)

    // ===== Container roundtrip =====

    @Test
    fun containerRoundtrip() {
        val env = ContainerCodec.sealPayload(
            dek,
            "id-1",
            RecordPayload("описание", "пароль 🔐", 1L, 2L)
        )

        val c = container(listOf(env))

        val back = ContainerCodec.parse(
            ContainerCodec.serialize(c)
        )

        assertEquals(c.kdf.memoryKiB, back.kdf.memoryKiB)
        assertEquals(c.kdf.iterations, back.kdf.iterations)
        assertEquals(c.kdf.parallelism, back.kdf.parallelism)

        assertTrue(c.kdf.salt.contentEquals(back.kdf.salt))

        assertEquals(c.wraps.size, back.wraps.size)
        assertEquals(c.records.size, back.records.size)

        assertEquals("id-1", back.records[0].id)

        assertTrue(
            c.records[0].ct.contentEquals(back.records[0].ct)
        )
    }

    @Test
    fun emptyRecordsRoundtrip() {
        val back = ContainerCodec.parse(
            ContainerCodec.serialize(container())
        )

        assertEquals(0, back.records.size)
    }

    // ===== Header validation =====

    @Test
    fun parseRejectsWrongMagic() {
        val bad = ContainerCodec.serialize(container())
            .replace("PMV3", "XXXX")

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.parse(bad)
        }
    }

    @Test
    fun parseRejectsWrongVersion() {
        val bad = ContainerCodec.serialize(container())
            .replace(
                "\"formatVersion\": 3",
                "\"formatVersion\": 2"
            )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.parse(bad)
        }
    }

    @Test
    fun parseRejectsWrongKdfAlg() {
        val bad = ContainerCodec.serialize(container())
            .replace("argon2id", "pbkdf2")

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.parse(bad)
        }
    }

    @Test
    fun parseToleratesUnknownFields() {
        val withExtra = ContainerCodec.serialize(container())
            .replaceFirst(
                "{",
                "{\n  \"futureField\": 42,"
            )

        assertEquals(
            1,
            ContainerCodec.parse(withExtra).wraps.size
        )
    }

    // ===== KDF validation =====

    @Test
    fun serializeRejectsInvalidKdfMemory() {
        val invalidKdf = KdfConfig(
            memoryKiB = 7,
            iterations = 1,
            parallelism = 1,
            salt = salt
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(
                container(config = invalidKdf)
            )
        }
    }

    @Test
    fun parseRejectsInvalidKdfMemory() {
        val bad = ContainerCodec.serialize(container())
            .replace(
                "\"memoryKiB\": 8192",
                "\"memoryKiB\": 7"
            )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.parse(bad)
        }
    }

    @Test
    fun serializeRejectsKdfIterationsAboveLimit() {
        val invalidKdf = KdfConfig(
            memoryKiB = 8192,
            iterations = Argon2Kdf.MAX_ITERATIONS + 1,
            parallelism = 1,
            salt = salt
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(
                container(config = invalidKdf)
            )
        }
    }

    @Test
    fun serializeRejectsKdfParallelismAboveLimit() {
        val invalidKdf = KdfConfig(
            memoryKiB = 8192,
            iterations = 1,
            parallelism = Argon2Kdf.MAX_PARALLELISM + 1,
            salt = salt
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(
                container(config = invalidKdf)
            )
        }
    }

    @Test
    fun serializeRejectsWrongSaltSize() {
        val invalidKdf = KdfConfig(
            memoryKiB = 8192,
            iterations = 1,
            parallelism = 1,
            salt = ByteArray(15)
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(
                container(config = invalidKdf)
            )
        }
    }

    // ===== Wrap validation =====

    @Test
    fun serializeRejectsMissingMasterWrap() {
        val invalidWrap = KeyHierarchy.Wrap(
            "other",
            ByteArray(12),
            ByteArray(48)
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(
                container(wraps = listOf(invalidWrap))
            )
        }
    }

    @Test
    fun serializeRejectsWrongMasterWrapSize() {
        val invalidWrap = KeyHierarchy.Wrap(
            KeyHierarchy.WRAP_TYPE_MASTER,
            ByteArray(12),
            ByteArray(47)
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(
                container(wraps = listOf(invalidWrap))
            )
        }
    }

    @Test
    fun serializeRejectsWrongWrapIvSize() {
        val invalidWrap = KeyHierarchy.Wrap(
            KeyHierarchy.WRAP_TYPE_MASTER,
            ByteArray(11),
            ByteArray(48)
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(
                container(wraps = listOf(invalidWrap))
            )
        }
    }

    // ===== Record validation =====

    @Test
    fun serializeRejectsDuplicateRecordIds() {
        val env = ContainerCodec.sealPayload(
            dek,
            "id-1",
            RecordPayload("a", "b", 1L, 2L)
        )

        val c = container(listOf(env, env))

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.serialize(c)
        }
    }

    @Test
    fun parseRejectsDuplicateIds() {
        val first = ContainerCodec.sealPayload(
            dek,
            "id-1",
            RecordPayload("a", "b", 1L, 2L)
        )

        val second = ContainerCodec.sealPayload(
            dek,
            "id-2",
            RecordPayload("c", "d", 3L, 4L)
        )

        val validJson = ContainerCodec.serialize(
            container(listOf(first, second))
        )

        // Создаём некорректный JSON уже после сериализации.
        val invalidJson = validJson.replace(
            "\"id\": \"id-2\"",
            "\"id\": \"id-1\""
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContainerCodec.parse(invalidJson)
        }
    }

    // ===== Payload roundtrip =====

    @Test
    fun payloadRoundtripUnicode() {
        val p = RecordPayload(
            "описание ñ 日本語",
            "пароль 🔐",
            10L,
            20L,
            false
        )

        val env = ContainerCodec.sealPayload(
            dek,
            "id-9",
            p
        )

        assertEquals(
            p,
            ContainerCodec.openPayload(dek, env)
        )
    }

    @Test
    fun payloadWrongIdFails() {
        val env = ContainerCodec.sealPayload(
            dek,
            "id-1",
            RecordPayload("a", "b", 1L, 2L)
        )

        assertNull(
            ContainerCodec.openPayload(
                dek,
                env.copy(id = "id-2")
            )
        )
    }

    @Test
    fun payloadWrongDekFails() {
        val env = ContainerCodec.sealPayload(
            dek,
            "id-1",
            RecordPayload("a", "b", 1L, 2L)
        )

        assertNull(
            ContainerCodec.openPayload(
                KeyHierarchy.newDek(),
                env
            )
        )
    }

    @Test
    fun tombstoneRoundtrip() {
        val env = ContainerCodec.sealPayload(
            dek,
            "id-1",
            RecordPayload(
                "a",
                "b",
                1L,
                2L,
                deleted = true
            )
        )

        assertTrue(
            ContainerCodec.openPayload(dek, env)!!.deleted
        )
    }
}