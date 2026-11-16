package com.c0dex.core.io

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Файловый слой десктопа: честная атомарная запись tmp+rename (D-013). */
object VaultFileIO {

    fun read(p: Path): String? = try {
        Files.readString(p, StandardCharsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun writeAtomic(p: Path, text: String): Boolean = try {
        val tmp = Files.createTempFile(p.parent ?: Path.of("."), ".pmv3-", ".tmp")
        Files.writeString(tmp, text, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, p, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (e: Exception) {
        false
    }
}