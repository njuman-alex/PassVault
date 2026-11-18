package com.c0dex.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.c0dex.desktop.data.DesktopPrefs
import com.c0dex.desktop.data.DesktopRepository
import com.c0dex.desktop.session.DesktopClipboard
import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.ui.lock.LockPane
import com.c0dex.desktop.ui.settings.SettingsPane
import com.c0dex.desktop.ui.vault.DetailPane
import com.c0dex.desktop.ui.vault.ListPane
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.beans.PropertyChangeListener
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.WindowPosition

fun main() = application {
    val scope = rememberCoroutineScope()
    val prefs = remember { DesktopPrefs() }
    val session = remember { DesktopSession(scope, prefs) }
    val repo = remember { DesktopRepository(session) }
    val clipboard = remember { DesktopClipboard(scope, prefs) }
    var showSettings by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "PassVault",
        state = rememberWindowState(
            size = DpSize(500.dp, 900.dp),
            position = WindowPosition.Aligned(Alignment.Center)
        )
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val st by session.state.collectAsState()

            // автолок/лок из любой точки — сбрасываем навигацию, чтобы после разлока не висела старая деталь
            LaunchedEffect(st) {
                if (st is DesktopSession.State.Locked) { detailId = null; showSettings = false }
            }

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
                val openId = detailId
                when {
                    st is DesktopSession.State.Locked -> LockPane(session, prefs)
                    openId != null -> DetailPane(openId, repo, clipboard, onBack = { detailId = null })
                    showSettings -> SettingsPane(session, prefs, onBack = { showSettings = false })
                    else -> ListPane(session, repo, clipboard, onOpen = { detailId = it }, onSettings = { showSettings = true })
                }
            }
        }
    }
}