package com.c0dex.passvault.data

import android.content.Context

/**
 * Настройки и служебные счётчики. НИКАКИХ секретов:
 * мастер/DEK живут только в памяти сессии (SESSION.md), здесь — только Uri и таймауты.
 */
class SettingsPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("passvault_settings", Context.MODE_PRIVATE)

    var vaultUri: String?
        get() = prefs.getString(KEY_VAULT_URI, null)
        set(v) = prefs.edit().putString(KEY_VAULT_URI, v).apply()

    var idleTimeoutMs: Long
        get() = prefs.getLong(KEY_IDLE, DEFAULT_IDLE)
        set(v) = prefs.edit().putLong(KEY_IDLE, v).apply()

    var clipboardTimeoutMs: Long
        get() = prefs.getLong(KEY_CLIPBOARD, DEFAULT_CLIPBOARD)
        set(v) = prefs.edit().putLong(KEY_CLIPBOARD, v).apply()

    var backgroundGraceMs: Long
        get() = prefs.getLong(KEY_GRACE, DEFAULT_GRACE)
        set(v) = prefs.edit().putLong(KEY_GRACE, v).apply()

    var failCount: Int
        get() = prefs.getInt(KEY_FAIL, 0)
        set(v) = prefs.edit().putInt(KEY_FAIL, v).apply()

    var lastActiveTs: Long
        get() = prefs.getLong(KEY_LAST_ACTIVE, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_ACTIVE, v).apply()

    companion object {
        const val DEFAULT_IDLE = 5 * 60_000L        // D-008: 5 мин
        const val DEFAULT_CLIPBOARD = 30_000L       // D-008: 30 с
        const val DEFAULT_GRACE = 0L                // D-008: сразу

        private const val KEY_VAULT_URI = "vault_uri"
        private const val KEY_IDLE = "idle_timeout_ms"
        private const val KEY_CLIPBOARD = "clipboard_timeout_ms"
        private const val KEY_GRACE = "background_grace_ms"
        private const val KEY_FAIL = "fail_count"
        private const val KEY_LAST_ACTIVE = "last_active_ts"
    }
}