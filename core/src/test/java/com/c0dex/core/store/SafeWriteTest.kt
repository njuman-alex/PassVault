package com.c0dex.core.store

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class SafeWriteTest {

    private class Fake(var content: String? = null) : SafeWrite.Target {

        var failWrite = false
        var failAtomically = false
        var torn = false
        var writes = 0

        override fun read(): String? = content

        override fun delete() { content = null }

        /*
override fun write(text: String): Boolean {
    if (failAtomically) return false

    val tornNow = torn
    torn = false

    content = if (tornNow || failWrite) {
        text.take(text.length / 2)
    } else {
        text
    }

    return !failWrite
}
*/

        // НОВАЯ ВЕРСИЯ:
        override fun write(text: String): Boolean {
            writes++
            if (failAtomically) return false

            val tornNow = torn
            torn = false

            // Сбой failWrite действует только на одну запись.
            val failNow = failWrite
            failWrite = false

            content = if (tornNow || failNow) {
                text.take(text.length / 2)
            } else {
                text
            }

            return !failNow
        }
    }

    @Test
    fun atomicFailureDoesNotRewriteGoodMain() {
        val main = Fake("old"); val backup = Fake("old")
        main.failAtomically = true
        assertFalse(SafeWrite.write(main, backup, "new"))
        assertEquals("old", main.content)
        assertEquals(1, main.writes)   // только попытка записи, откатной не было
    }

    @Test
    fun happyWriteUpdatesBackup() {
        val main = Fake("old")
        val backup = Fake("old")

        assertTrue(SafeWrite.write(main, backup, "new"))
        assertEquals("new", main.content)
        assertEquals("new", backup.content)
    }

    @Test
    fun tornWriteRollsBackToBackup() {
        val main = Fake("old")
        val backup = Fake("old")

        main.torn = true

        assertFalse(SafeWrite.write(main, backup, "new"))
        assertEquals("old", main.content)
        assertEquals("old", backup.content)
    }

    @Test
    fun failedWriteKeepsBackupIntact() {
        val main = Fake("old")
        val backup = Fake("old")

        main.failWrite = true

        assertFalse(SafeWrite.write(main, backup, "new"))
        assertEquals("old", main.content)
        assertEquals("old", backup.content)
    }

    @Test
    fun firstTornWriteHasNothingToRollBackTo() {
        val main = Fake()
        val backup = Fake()

        main.torn = true

        assertFalse(SafeWrite.write(main, backup, "new"))
        assertNull(backup.content)
    }

    @Test
    fun backupFailureRollsBackMain() {
        val main = Fake("old")
        val backup = Fake("old")

        backup.failAtomically = true

        assertFalse(SafeWrite.write(main, backup, "new"))

        assertEquals("old", main.content)
        assertEquals("old", backup.content)
    }

    @Test
    fun firstBackupFailureKeepsVerifiedMain() {
        val main = Fake()
        val backup = Fake()

        backup.failAtomically = true

        assertFalse(SafeWrite.write(main, backup, "new"))

        // Старой копии нет, но основной файл уже проверен.
        assertEquals("new", main.content)
        assertNull(backup.content)
    }

    @Test
    fun recoverRestoresMainFromBackup() {
        val main = Fake("torn")
        val backup = Fake("good")

        assertTrue(SafeWrite.recover(main, backup))
        assertEquals("good", main.content)
    }

    @Test
    fun recoverWithoutBackupFails() {
        val main = Fake("torn")
        val backup = Fake()

        assertFalse(SafeWrite.recover(main, backup))
        assertEquals("torn", main.content)
    }

    @Test fun tornBackupRestoresPreviousGood() {
        val main = Fake("old"); val backup = Fake("old")
        backup.torn = true
        assertFalse(SafeWrite.write(main, backup, "new"))
        assertEquals("old", main.content)
        assertEquals("old", backup.content)
    }
    @Test fun tornFirstBackupDeleted() {
        val main = Fake(); val backup = Fake()
        backup.torn = true
        assertFalse(SafeWrite.write(main, backup, "new"))
        assertEquals("new", main.content)   // main проверен, откатывать не из чего
        assertNull(backup.content)          // рваный первый backup удалён
    }
}