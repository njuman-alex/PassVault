package com.c0dex.core.store

/**
 * Crash-safe протокол записи сейфа (D-022), платформенно-нейтральный.
 *
 * write: main.write -> verify перечитыванием -> backup обновляется;
 *        verify fail -> откат main из last-good backup, false.
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

        // Запоминаем последнюю хорошую копию до изменения основного файла.
        val previousGood = backup.read()

        // было: fun rollback() { previousGood?.let { old -> main.write(old); main.read() == old } }
        fun rollback() {
            val old = previousGood ?: return
            if (main.read() == old) return   // D-032: main не менялся — лишняя truncate-запись не нужна
            main.write(old)                  // best-effort; если порвётся — лечит кнопка recover
        }

        // Записываем основной файл.
        if (!main.write(text)) {
            rollback()
            return false
        }

        // Проверяем основной файл перечитыванием.
        if (main.read() != text) {
            rollback()
            return false
        }

        // Обновляем backup и проверяем его содержимое.
        if (!backup.write(text) || backup.read() != text) {
            // D-029: backup не стал last-good — возвращаем previousGood или удаляем рваный первый
            if (previousGood != null) {
                if (!backup.write(previousGood) || backup.read() != previousGood) backup.delete()   // D-032
            } else backup.delete()
            rollback()
            return false
        }

        return true
    }

    fun recover(main: Target, backup: Target): Boolean {
        val good = backup.read() ?: return false
        if (!main.write(good)) return false
        return main.read() == good
    }
}