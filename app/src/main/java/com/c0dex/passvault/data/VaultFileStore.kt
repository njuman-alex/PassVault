package com.c0dex.passvault.data

import android.content.Context
import android.net.Uri
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.VaultFiles
import com.c0dex.core.store.SafeWrite
import java.io.File
import java.security.MessageDigest
import java.io.ByteArrayOutputStream

/**
 * Файловый доступ к vault через SAF.
 *
 * D-011: запись = прямая truncate-запись по стабильному Uri (rename отклонён).
 * D-022: поверх truncate — crash-safe протокол SafeWrite.kt: verify перечитыванием,
 * last-good backup в приватной папке (там tmp+rename безопасен), явное восстановление.
 */
class VaultFileStore(private val context: Context) {

    /*
// СТАРАЯ ВЕРСИЯ:

fun read(uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use {
        it.readBytes().toString(Charsets.UTF_8)
    }
} catch (e: Exception) {
    null
}
*/

    // НОВАЯ ВЕРСИЯ:

    fun read(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->

            //val maxBytes = ContainerCodec.MAX_CONTAINER_CHARS.toLong()
            //val maxBytes = ContainerCodec.MAX_CONTAINER_BYTES.toLong()   // D-029
            val maxBytes = ContainerCodec.MAX_READ_BYTES.toLong()

            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)

            var total = 0L

            while (true) {
                val count = input.read(buffer)

                if (count < 0) break
                if (count == 0) continue

                total += count

                if (total > maxBytes) {
                    return null
                }

                output.write(buffer, 0, count)
            }

            output.toString(Charsets.UTF_8.name())
        }
    } catch (e: Exception) {
        null
    }

    fun write(uri: Uri, text: String): Boolean =
        SafeWrite.write(mainTarget(uri), backupTarget(uri), text)

    fun hasBackup(uri: Uri): Boolean = backupFile(uri).let { it.exists() && it.length() > 0 }

    fun recover(uri: Uri): Boolean = SafeWrite.recover(mainTarget(uri), backupTarget(uri))

    private fun rawWrite(uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } != null
    } catch (e: Exception) {
        false
    }

    private fun mainTarget(uri: Uri) = object : SafeWrite.Target {
        override fun read(): String? = this@VaultFileStore.read(uri)
        override fun write(text: String): Boolean = rawWrite(uri, text)
    }

    private fun backupTarget(uri: Uri) = object : SafeWrite.Target {
        override fun read(): String? = try {
            backupFile(uri).takeIf { it.exists() }?.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
        override fun write(text: String): Boolean = try {
            val f = backupFile(uri)
            val tmp = File.createTempFile(VaultFiles.TEMP_PREFIX, ".tmp", f.parentFile)
            tmp.writeText(text, Charsets.UTF_8)
            val ok = tmp.renameTo(f)
            if (!ok) tmp.delete()
            ok
        } catch (e: Exception) {
            false
        }
        override fun delete() { runCatching { backupFile(uri).delete() } }   // D-029
    }

    /** Пер-сейф backup: имя = хэш Uri, чтобы копия старого сейфа не предложила себя новому. */
    private fun backupFile(uri: Uri): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return File(context.filesDir, VaultFiles.BACKUP_PREFIX + hash + VaultFiles.EXT)
    }
}