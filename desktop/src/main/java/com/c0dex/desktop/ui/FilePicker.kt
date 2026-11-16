package com.c0dex.desktop.ui

import java.nio.file.Path
import javax.swing.JFileChooser

fun chooseFile(save: Boolean): Path? {
    val fc = JFileChooser()
    val ok = if (save) fc.showSaveDialog(null) else fc.showOpenDialog(null)
    return if (ok == JFileChooser.APPROVE_OPTION) fc.selectedFile.toPath() else null
}