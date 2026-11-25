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
    }

    fun write(main: Target, backup: Target, text: String): Boolean {
        if (!main.write(text)) return false
        if (main.read() != text) {
            backup.read()?.let { main.write(it) }
            return false
        }
        backup.write(text)
        return true
    }

    fun recover(main: Target, backup: Target): Boolean {
        val good = backup.read() ?: return false
        if (!main.write(good)) return false
        return main.read() == good
    }
}