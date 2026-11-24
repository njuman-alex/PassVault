package com.c0dex.core.store

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class SafeWriteTest {

    private class Fake(var content: String? = null) : SafeWrite.Target {
        var failWrite = false
        var torn = false
        override fun read(): String? = content
        override fun write(text: String): Boolean {
            val tornNow = torn
            torn = false   // разовый сбой: откатная запись проходит нормально
            content = if (tornNow || failWrite) text.take(text.length / 2) else text
            return !failWrite
        }
    }

    @Test fun happyWriteUpdatesBackup() {
        val main = Fake("old"); val backup = Fake("old")
        assertTrue(SafeWrite.write(main, backup, "new"))
        assertEquals("new", main.content)
        assertEquals("new", backup.content)
    }

    @Test fun tornWriteRollsBackToBackup() {
        val main = Fake("old"); val backup = Fake("old")
        main.torn = true
        assertFalse(SafeWrite.write(main, backup, "new"))
        assertEquals("old", main.content)   // откатили предыдущее хорошее
        assertEquals("old", backup.content)
    }

    @Test fun failedWriteKeepsBackupIntact() {
        val main = Fake("old"); val backup = Fake("old")
        main.failWrite = true
        assertFalse(SafeWrite.write(main, backup, "new"))
        assertEquals("old", backup.content)
    }

    @Test fun firstTornWriteHasNothingToRollBackTo() {
        val main = Fake(); val backup = Fake()
        main.torn = true
        assertFalse(SafeWrite.write(main, backup, "new"))
        assertNull(backup.content)
    }

    @Test fun recoverRestoresMainFromBackup() {
        val main = Fake("torn"); val backup = Fake("good")
        assertTrue(SafeWrite.recover(main, backup))
        assertEquals("good", main.content)
    }

    @Test fun recoverWithoutBackupFails() {
        val main = Fake("torn"); val backup = Fake()
        assertFalse(SafeWrite.recover(main, backup))
        assertEquals("torn", main.content)
    }
}