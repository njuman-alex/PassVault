package com.c0dex.passvault.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Файловый доступ к vault-контейнеру через SAF (Storage Access Framework).
 *
 * Контракт:
 *  - чтение/запись по Uri, выданному системным picker'ом (папка синхронизации);
 *  - запись best-effort атомарная: tmp -> bak -> rename (окно уязвимости минимально);
 *    если провайдер не даёт родителя (single-uri) — фоллбэк на прямую запись;
 *  - никаких секретов здесь: только байты контейнера (они уже зашифрованы ядром).
 */
class VaultFileStore(private val context: Context) {

    fun read(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    fun write(uri: Uri, text: String): Boolean =
        writeAtomic(uri, text) || writeDirect(uri, text)

    /** Создаёт новый контейнер внутри выбранной папки-дерева. */
    fun createInTree(treeUri: Uri, name: String): Uri? = try {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        tree.createFile("application/octet-stream", name)?.uri
    } catch (e: Exception) {
        null
    }

    private fun writeDirect(uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } != null
    } catch (e: Exception) {
        false
    }

    private fun writeAtomic(uri: Uri, text: String): Boolean = try {
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return false
        val parent = doc.parentFile ?: return false
        val name = doc.name ?: return false
        val bytes = text.toByteArray(Charsets.UTF_8)

        val tmp = parent.createFile("application/octet-stream", "$name.tmp") ?: return false
        val ok = context.contentResolver.openOutputStream(tmp.uri, "wt")?.use { it.write(bytes) } != null
        if (!ok) { tmp.delete(); return false }

        // подмена через bak: при краше между шагами recoverable (.bak или .tmp)
        if (!doc.renameTo("$name.bak")) { tmp.delete(); return false }
        if (!tmp.renameTo(name)) { doc.renameTo(name); return false }
        parent.findFile("$name.bak")?.delete()
        true
    } catch (e: Exception) {
        false
    }
}