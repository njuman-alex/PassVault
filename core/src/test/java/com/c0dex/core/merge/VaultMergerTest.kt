package com.c0dex.core.merge

import com.c0dex.core.crypto.KeyHierarchy
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.KdfConfig
import com.c0dex.core.format.RecordEnvelope
import com.c0dex.core.format.RecordPayload
import com.c0dex.core.format.VaultContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMergerTest {

    private val dek = KeyHierarchy.newDek()
    private val kdf = KdfConfig(8192, 1, 1, ByteArray(16))
    private val wrap = KeyHierarchy.Wrap("master", ByteArray(12), ByteArray(48))

    private fun env(id: String, updatedAt: Long, deleted: Boolean = false, desc: String = "d"): RecordEnvelope =
        ContainerCodec.sealPayload(dek, id, RecordPayload(desc, "p", 1L, updatedAt, deleted))

    private fun container(records: List<RecordEnvelope>) = VaultContainer(kdf, listOf(wrap), records)

    private fun ids(c: VaultContainer) = c.records.map { it.id }

    @Test
    fun unionOfDisjointIds() {
        val a = container(listOf(env("a", 10)))
        val b = container(listOf(env("b", 10)))
        assertEquals(listOf("a", "b"), ids(VaultMerger.merge(dek, a, b)))
    }

    @Test
    fun newerUpdatedAtWins() {
        val a = container(listOf(env("x", 10, desc = "old")))
        val b = container(listOf(env("x", 20, desc = "new")))
        val m = VaultMerger.merge(dek, a, b)
        assertEquals("new", ContainerCodec.openPayload(dek, m.records[0])!!.description)
    }

    @Test
    fun newerTombstoneWins() {
        val a = container(listOf(env("x", 10)))
        val b = container(listOf(env("x", 20, deleted = true)))
        val m = VaultMerger.merge(dek, a, b)
        assertTrue(ContainerCodec.openPayload(dek, m.records[0])!!.deleted)
    }

    @Test
    fun newerLiveResurrectsOverOldTombstone() {
        val a = container(listOf(env("x", 20, deleted = true)))
        val b = container(listOf(env("x", 30)))
        val m = VaultMerger.merge(dek, a, b)
        assertTrue(!ContainerCodec.openPayload(dek, m.records[0])!!.deleted)
    }

    @Test
    fun tieUpdatedAtTombstoneWins() {
        val a = container(listOf(env("x", 10)))
        val b = container(listOf(env("x", 10, deleted = true)))
        val m = VaultMerger.merge(dek, a, b)
        assertTrue(ContainerCodec.openPayload(dek, m.records[0])!!.deleted)
    }

    @Test
    fun fullTieIsSymmetric() {
        val a = container(listOf(env("x", 10, desc = "one")))
        val b = container(listOf(env("x", 10, desc = "two")))
        val ab = VaultMerger.merge(dek, a, b)
        val ba = VaultMerger.merge(dek, b, a)
        assertTrue(ab.records[0].ct.contentEquals(ba.records[0].ct))
    }

    @Test
    fun idempotentSelfMerge() {
        val a = container(listOf(env("x", 10), env("y", 5)))
        val m = VaultMerger.merge(dek, a, a)
        assertEquals(ids(a), ids(m))
        assertTrue(a.records[0].ct.contentEquals(m.records[0].ct))
    }

    @Test
    fun mergeThenMergeAgainStable() {
        val a = container(listOf(env("x", 10)))
        val b = container(listOf(env("x", 20), env("z", 1)))
        val ab = VaultMerger.merge(dek, a, b)
        val abb = VaultMerger.merge(dek, ab, b)
        assertEquals(ids(ab), ids(abb))
    }

    @Test
    fun corruptSideYieldsToReadable() {
        val good = env("x", 10)
        val corrupt = good.copy(ct = good.ct.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        val a = container(listOf(corrupt))
        val b = container(listOf(good))
        val m = VaultMerger.merge(dek, a, b)
        assertTrue(m.records[0].ct.contentEquals(good.ct))
    }

    @Test
    fun outputSortedById() {
        val a = container(listOf(env("z", 1), env("a", 1), env("m", 1)))
        assertEquals(listOf("a", "m", "z"), ids(VaultMerger.merge(dek, a, container(emptyList()))))
    }
}