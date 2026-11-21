package com.c0dex.core.format

import com.c0dex.core.crypto.AesGcm
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class LoginPayloadTest {

    private val dek = ByteArray(32) { (it * 7 + 1).toByte() }
    private val id = "11111111-1111-4111-8111-111111111111"

    @Test fun roundtripWithLogin() {
        val p = RecordPayload("Госуслуги", "pass", 1L, 2L, false, "user@mail")
        val env = ContainerCodec.sealPayload(dek, id, p)
        assertEquals(p, ContainerCodec.openPayload(dek, env))
    }

    @Test fun oldPayloadWithoutLoginReadsEmpty() {
        val old = """{"description":"Госуслуги","password":"pass","createdAt":1,"updatedAt":2,"deleted":false}"""
        val enc = AesGcm.encrypt(dek, old.toByteArray(Charsets.UTF_8), id.toByteArray(Charsets.UTF_8))
        val p = ContainerCodec.openPayload(dek, RecordEnvelope(id, enc.iv, enc.ciphertext))
        assertNotNull(p)
        assertEquals("", p!!.login)
        assertEquals("Госуслуги", p.description)
    }

    @Test fun defaultLoginIsEmpty() {
        assertEquals("", RecordPayload("d", "p", 1L, 2L).login)
    }

    @Test fun unknownFieldsTolerated() {
        val future = """{"description":"d","password":"p","createdAt":1,"updatedAt":2,"deleted":false,"login":"u","notes":"x"}"""
        val enc = AesGcm.encrypt(dek, future.toByteArray(Charsets.UTF_8), id.toByteArray(Charsets.UTF_8))
        val p = ContainerCodec.openPayload(dek, RecordEnvelope(id, enc.iv, enc.ciphertext))
        assertNotNull(p)
        assertEquals("u", p!!.login)
    }
}