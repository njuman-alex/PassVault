package com.c0dex.core.format

import com.c0dex.core.crypto.KeyHierarchy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerCodecTest {

    private val dek = KeyHierarchy.newDek()
    private val salt = ByteArray(16) { it.toByte() }
    private val kdf = KdfConfig(8192, 1, 1, salt)
    private val wrap = KeyHierarchy.Wrap("master", ByteArray(12) { it.toByte() }, ByteArray(48) { it.toByte() })

    private fun container(records: List<RecordEnvelope> = emptyList()) =
        VaultContainer(kdf, listOf(wrap), records)

    @Test
    fun containerRoundtrip() {
        val env = ContainerCodec.sealPayload(dek, "id-1", RecordPayload("описание", "пароль 🔐", 1L, 2L))
        val c = container(listOf(env))
        val back = ContainerCodec.parse(ContainerCodec.serialize(c))
        assertEquals(c.kdf.memoryKiB, back.kdf.memoryKiB)
        assertEquals(c.wraps.size, back.wraps.size)
        assertEquals(c.records.size, back.records.size)
        assertEquals("id-1", back.records[0].id)
        assertTrue(c.records[0].ct.contentEquals(back.records[0].ct))
    }

    @Test
    fun emptyRecordsRoundtrip() {
        assertEquals(0, ContainerCodec.parse(ContainerCodec.serialize(container())).records.size)
    }

    @Test
    fun parseRejectsWrongMagic() {
        val bad = ContainerCodec.serialize(container()).replace("PMV3", "XXXX")
        assertThrows(IllegalArgumentException::class.java) { ContainerCodec.parse(bad) }
    }

    @Test
    fun parseRejectsWrongVersion() {
        val bad = ContainerCodec.serialize(container()).replace("\"formatVersion\": 3", "\"formatVersion\": 2")
        assertThrows(IllegalArgumentException::class.java) { ContainerCodec.parse(bad) }
    }

    @Test
    fun parseRejectsWrongKdfAlg() {
        val bad = ContainerCodec.serialize(container()).replace("argon2id", "pbkdf2")
        assertThrows(IllegalArgumentException::class.java) { ContainerCodec.parse(bad) }
    }

    @Test
    fun parseToleratesUnknownFields() {
        val withExtra = ContainerCodec.serialize(container()).replaceFirst("{", "{\n  \"futureField\": 42,")
        assertEquals(1, ContainerCodec.parse(withExtra).wraps.size)
    }

    @Test
    fun parseRejectsDuplicateIds() {
        val env = ContainerCodec.sealPayload(dek, "id-1", RecordPayload("a", "b", 1L, 2L))
        val c = container(listOf(env, env))
        assertThrows(IllegalArgumentException::class.java) { ContainerCodec.parse(ContainerCodec.serialize(c)) }
    }

    @Test
    fun payloadRoundtripUnicode() {
        val p = RecordPayload("описание ñ 日本語", "пароль 🔐", 10L, 20L, false)
        val env = ContainerCodec.sealPayload(dek, "id-9", p)
        assertEquals(p, ContainerCodec.openPayload(dek, env))
    }

    @Test
    fun payloadWrongIdFails() {
        val env = ContainerCodec.sealPayload(dek, "id-1", RecordPayload("a", "b", 1L, 2L))
        assertNull(ContainerCodec.openPayload(dek, env.copy(id = "id-2")))
    }

    @Test
    fun payloadWrongDekFails() {
        val env = ContainerCodec.sealPayload(dek, "id-1", RecordPayload("a", "b", 1L, 2L))
        assertNull(ContainerCodec.openPayload(KeyHierarchy.newDek(), env))
    }

    @Test
    fun tombstoneRoundtrip() {
        val env = ContainerCodec.sealPayload(dek, "id-1", RecordPayload("a", "b", 1L, 2L, deleted = true))
        assertTrue(ContainerCodec.openPayload(dek, env)!!.deleted)
    }
}