package com.c0dex.passvault.session

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.c0dex.passvault.data.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Политика буфера обмена (SESSION.md §5):
 * sensitive-флаг (Android 13+) + автоочистка через clipboardTimeoutMs.
 */
class ClipboardHelper(
    private val context: Context,
    private val prefs: SettingsPrefs,
    private val scope: CoroutineScope
) {
    private val cm: ClipboardManager = context.getSystemService(ClipboardManager::class.java)

    fun copySensitive(label: String, value: String) {
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        cm.setPrimaryClip(clip)
        val timeout = prefs.clipboardTimeoutMs
        if (timeout > 0) {
            scope.launch {
                delay(timeout)
                clearIfStill(value)
            }
        }
    }

    private fun clearIfStill(value: String) {
        val current = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (current == value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}