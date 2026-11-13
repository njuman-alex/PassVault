package com.c0dex.passvault.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Файловый доступ к vault через SAF.
 *
 * D-011: запись = прямая truncate-запись по стабильному Uri.
 * Атомарный rename через SAF отклонён: он меняет document-id и ломает
 * сохранённую ссылку. Восстановление при краше = версии файла в облаке.
 */
class VaultFileStore(private val context: Context) {

    fun read(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    fun write(uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } != null
    } catch (e: Exception) {
        false
    }

    fun createInTree(treeUri: Uri, name: String): Uri? = try {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        tree.createFile("application/octet-stream", name)?.uri
    } catch (e: Exception) {
        null
    }
}