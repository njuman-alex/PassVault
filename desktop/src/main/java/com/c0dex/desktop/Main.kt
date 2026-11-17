package com.c0dex.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.c0dex.desktop.data.DesktopPrefs
import com.c0dex.desktop.session.DesktopClipboard
import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.ui.lock.LockPane
import com.c0dex.desktop.ui.settings.SettingsPane
import com.c0dex.desktop.ui.vault.ListPane
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.beans.PropertyChangeListener

fun main() = application {
    val scope = rememberCoroutineScope()
    val prefs = remember { DesktopPrefs() }
    val session = remember { DesktopSession(scope, prefs) }
    val clipboard = remember { DesktopClipboard(scope, prefs) }
    var showSettings by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "PassVault",
        state = rememberWindowState(size = DpSize(500.dp, 900.dp))
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val st by session.state.collectAsState()
            DisposableEffect(Unit) {
                val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                var wasOurs = true
                val listener = PropertyChangeListener { evt ->
                    if (evt.propertyName == "focusedWindow") {
                        val w = evt.newValue as? Window
                        val nowOurs = w != null && Window.getWindows().contains(w)
                        if (nowOurs && !wasOurs) session.onFocusGained()
                        if (!nowOurs && wasOurs) session.onFocusLost()
                        wasOurs = nowOurs
                    }
                }
                kfm.addPropertyChangeListener(listener)
                onDispose { kfm.removePropertyChangeListener(listener) }
            }
            Surface(Modifier.fillMaxSize()) {
                if (st is DesktopSession.State.Locked) LockPane(session, prefs)
                else if (showSettings) SettingsPane(session, prefs, onBack = { showSettings = false })
                else ListPane(session, clipboard, onSettings = { showSettings = true })
            }
        }
    }
}