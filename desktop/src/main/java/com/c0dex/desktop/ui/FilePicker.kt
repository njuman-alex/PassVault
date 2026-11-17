package com.c0dex.desktop.ui

import com.c0dex.core.format.VaultFiles
import java.nio.file.Path
import javax.swing.JFileChooser

const val VAULT_EXT = VaultFiles.EXT

/** Выбор файла: save-режим дописывает .qdex, если расширения нет (как createInTree на Android). */
fun chooseFile(save: Boolean): Path? {
    val fc = JFileChooser()
    val ok = if (save) fc.showSaveDialog(null) else fc.showOpenDialog(null)
    if (ok != JFileChooser.APPROVE_OPTION) return null
    var p = fc.selectedFile.toPath()
    if (save) {
        val name = p.fileName.toString()
        if (!name.endsWith(".$VAULT_EXT")) p = p.resolveSibling("$name.$VAULT_EXT")
    }
    return p
}