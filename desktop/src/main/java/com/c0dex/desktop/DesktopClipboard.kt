package com.c0dex.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Буфер десктопа: автоочистка через clipboardMs (SESSION.md §5, sensitive на десктопе n/a). */
class DesktopClipboard(private val scope: CoroutineScope, private val prefs: DesktopPrefs) {

    fun copy(text: String) {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        cb.setContents(StringSelection(text), null)
        val t = prefs.clipboardMs
        if (t > 0) scope.launch {
            delay(t)
            val cur = try { cb.getData(DataFlavor.stringFlavor) as? String } catch (e: Exception) { null }
            if (cur == text) cb.setContents(StringSelection(""), null)
        }
    }
}