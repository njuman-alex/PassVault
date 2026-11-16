package com.c0dex.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Настройки десктопа: properties в ~/.passvault (без секретов). */
class DesktopPrefs {
    private val file: Path = Path.of(System.getProperty("user.home"), ".passvault", "settings.properties")
    private val props = Properties().also {
        if (Files.exists(file)) Files.newInputStream(file).use(it::load)
    }

    private fun save() {
        Files.createDirectories(file.parent)
        Files.newOutputStream(file).use { props.store(it, "PassVault desktop") }
    }

    var vaultPath: String?
        get() = props.getProperty("vault_path")
        set(v) { if (v == null) props.remove("vault_path") else props.setProperty("vault_path", v); save() }

    var idleMs: Long
        get() = props.getProperty("idle_ms", "300000").toLong()
        set(v) { props.setProperty("idle_ms", v.toString()); save() }

    var graceMs: Long
        get() = props.getProperty("grace_ms", "0").toLong()
        set(v) { props.setProperty("grace_ms", v.toString()); save() }

    var failCount: Int
        get() = props.getProperty("fail_count", "0").toInt()
        set(v) { props.setProperty("fail_count", v.toString()); save() }

    var lastActiveTs: Long
        get() = props.getProperty("last_active", "0").toLong()
        set(v) { props.setProperty("last_active", v.toString()); save() }

    var clipboardMs: Long
        get() = props.getProperty("clipboard_ms", "30000").toLong()
        set(v) { props.setProperty("clipboard_ms", v.toString()); save() }
}