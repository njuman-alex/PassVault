package com.c0dex.core.store

import com.c0dex.core.format.ContainerCodec

/**
 * Crash-safe протокол записи сейфа (D-022), платформенно-нейтральный.
 *
 * write: текущий main сначала сверяется с backup и при необходимости
 *        сохраняется туда; затем main.write -> verify -> backup обновляется;
 *        verify fail -> откат main из актуальной предыдущей копии, false.
 * recover: явное восстановление main из backup (кнопка на лок-скрине).
 *
 * Атомарность backup обеспечивает платформа (приватная папка = tmp+rename);
 * main остаётся truncate-записью по стабильному Uri/пути (D-011).
 */
object SafeWrite {

    interface Target {
        fun read(): String?
        fun write(text: String): Boolean
        /** D-029: удалить копию (рваный первый backup). По умолчанию no-op. */
        fun delete() {}
    }

    /*
// СТАРАЯ ВЕРСИЯ write():

fun write(main: Target, backup: Target, text: String): Boolean {
    if (!main.write(text)) return false
    if (main.read() != text) {
        backup.read()?.let { main.write(it) }
        return false
    }
    backup.write(text)
    return true
}
*/

    // НОВАЯ ВЕРСИЯ:
    fun write(main: Target, backup: Target, text: String): Boolean {

        // Не начинаем запись файла, который платформенный read не сможет принять.
        if (!ContainerCodec.isWithinReadLimit(text)) return false

        // Сначала читаем именно текущее содержимое main.
        // Backup может отсутствовать или относиться к более старой версии файла.
        // Ошибка чтения main не должна трактоваться как отсутствие файла.
        // Платформенный Target обязан бросить исключение, если не смог прочитать Uri.
        val currentMain = try { main.read() } catch (e: Exception) { return false }
        val existingBackup = try { backup.read() } catch (e: Exception) { null }

        // Для существующего main готовим актуальную точку отката ДО его изменения.
        // Если backup отсутствует/устарел, обновляем его текущим содержимым main
        // и обязательно проверяем результат. При неудаче main не трогаем.
        val previousGood = if (currentMain != null) {
            if (existingBackup != currentMain) {
                val backupPrepared = try {
                    backup.write(currentMain) && backup.read() == currentMain
                } catch (e: Exception) {
                    false
                }
                if (!backupPrepared) return false
            }
            currentMain
        } else {
            // Новый/пустой main: сохраняем прежний fallback, если он имеется.
            existingBackup
        }

        fun rollback() {
            val old = previousGood ?: return
            val current = try { main.read() } catch (e: Exception) { null }
            if (current == old) return   // D-032: main не менялся — лишняя truncate-запись не нужна
            runCatching { main.write(old) } // best-effort; если порвётся — лечит кнопка recover
        }

        // Записываем основной файл только после подготовки актуального backup.
        val mainWritten = try { main.write(text) } catch (e: Exception) { false }
        if (!mainWritten) {
            rollback()
            return false
        }

        // Проверяем основной файл перечитыванием.
        val mainVerified = try { main.read() == text } catch (e: Exception) { false }
        if (!mainVerified) {
            rollback()
            return false
        }

        // Обновляем backup и проверяем его содержимое.
        val backupUpdated = try {
            backup.write(text) && backup.read() == text
        } catch (e: Exception) {
            false
        }
        if (!backupUpdated) {
            // D-029: backup не стал last-good — возвращаем previousGood или удаляем рваный первый
            if (previousGood != null) {
                val backupRestored = try {
                    backup.write(previousGood) && backup.read() == previousGood
                } catch (e: Exception) {
                    false
                }
                if (!backupRestored) {
                    // Удаляем backup только если старая копия действительно потеряна.
                    // При атомарном отказе записи прежнее содержимое остаётся целым.
                    val stillGood = try { backup.read() == previousGood } catch (e: Exception) { false }
                    if (!stillGood) runCatching { backup.delete() }
                }
            } else {
                // Первой хорошей копии не было — удаляем возможный рваный файл.
                runCatching { backup.delete() }
            }
            rollback()
            return false
        }

        return true
    }

    fun recover(main: Target, backup: Target): Boolean = try {
        val good = backup.read() ?: return false
        if (!main.write(good)) return false
        main.read() == good
    } catch (e: Exception) {
        false
    }
}
