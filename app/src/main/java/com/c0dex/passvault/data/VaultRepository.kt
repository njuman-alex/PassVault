package com.c0dex.passvault.data

import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.RecordPayload
import com.c0dex.core.format.VaultContainer
import com.c0dex.passvault.session.SessionManager
import java.util.UUID

/** CRUD записей поверх сессии. Секреты не хранит: всё через withDek. */
class VaultRepository(private val session: SessionManager) {

    data class RecordView(val id: String, val description: String, val password: String, val updatedAt: Long)

    fun list(): List<RecordView> = session.withDek { dek ->
        val c = session.currentContainer() ?: return@withDek emptyList()
        c.records.mapNotNull { env ->
            ContainerCodec.openPayload(dek, env)
                ?.takeIf { !it.deleted }
                ?.let { RecordView(env.id, it.description, it.password, it.updatedAt) }
        }.sortedBy { it.description.lowercase() }
    } ?: emptyList()

    fun add(description: String, password: String): Boolean {
        val c = session.currentContainer() ?: return false
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val env = session.withDek { ContainerCodec.sealPayload(it, id, RecordPayload(description, password, now, now)) }
            ?: return false
        return session.replaceContainer(c.copy(records = c.records + env))
    }

    fun update(id: String, description: String, password: String): Boolean {
        val c = session.currentContainer() ?: return false
        val env = c.records.firstOrNull { it.id == id } ?: return false
        val p = session.withDek { ContainerCodec.openPayload(it, env) } ?: return false
        val now = System.currentTimeMillis()
        val newEnv = session.withDek {
            ContainerCodec.sealPayload(it, id, p.copy(description = description, password = password, updatedAt = now))
        } ?: return false
        return session.replaceContainer(c.copy(records = c.records.map { if (it.id == id) newEnv else it }))
    }

    /** Удаление = tombstone (deleted=true), чтобы синк propagate-ил удаление. */
    fun delete(id: String): Boolean {
        val c = session.currentContainer() ?: return false
        val env = c.records.firstOrNull { it.id == id } ?: return false
        val p = session.withDek { ContainerCodec.openPayload(it, env) } ?: return false
        val now = System.currentTimeMillis()
        val newEnv = session.withDek {
            ContainerCodec.sealPayload(it, id, p.copy(deleted = true, updatedAt = now))
        } ?: return false
        return session.replaceContainer(c.copy(records = c.records.map { if (it.id == id) newEnv else it }))
    }
}