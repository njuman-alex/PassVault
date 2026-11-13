package com.c0dex.passvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.c0dex.passvault.data.SettingsPrefs
import com.c0dex.passvault.data.VaultFileStore
import com.c0dex.passvault.session.SessionManager
import com.c0dex.passvault.ui.lock.LockScreen
import com.c0dex.passvault.ui.theme.PassVaultTheme

/**
 * Single Activity + гейт по состоянию сессии (D-004).
 * LOCKED -> только лок-скрин; UNLOCKED -> оболочка vault (список придёт в шаге 10).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = SettingsPrefs(this)
        val store = VaultFileStore(this)
        val session = SessionManager(this, store, prefs, lifecycleScope)

        setContent {
            PassVaultTheme {
                val state by session.state.collectAsState()

                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> session.onBackground()
                            Lifecycle.Event.ON_START -> session.onForeground()
                            else -> {}
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }

                when (state) {
                    is SessionManager.State.Locked -> LockScreen(session, store, prefs)
                    //is SessionManager.State.Unlocked -> VaultShellPlaceholder(session)
                    is SessionManager.State.Unlocked -> VaultShellPlaceholder(onLock = { session.lock() })
                }
            }
        }
    }
}

/** Временная оболочка: список записей приедет в шаге 10. */
//@Composable
//private fun VaultShellPlaceholder(session: SessionManager) {
//    Column(
//        modifier = Modifier.fillMaxSize().padding(24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text("Vault разблокирован 🔓", style = MaterialTheme.typography.headlineSmall)
//        Spacer(Modifier.height(8.dp))
//        Text("Список записей появится в шаге 10", color = MaterialTheme.colorScheme.onSurfaceVariant)
//        Spacer(Modifier.height(24.dp))
//        Button(onClick = { session.lock() }) { Text("Заблокировать") }
//    }
//}

/** Временная оболочка: список записей приедет в шаге 10. */
@Composable
internal fun VaultShellPlaceholder(onLock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Vault разблокирован 🔓", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Список записей появится в шаге 10", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLock) { Text("Заблокировать") }
    }
}

@Preview(showBackground = true, name = "Shell")
@Composable
private fun VaultShellPreview() = PassVaultTheme { VaultShellPlaceholder {} }