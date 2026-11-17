package com.c0dex.passvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.c0dex.passvault.data.SettingsPrefs
import com.c0dex.passvault.data.VaultFileStore
import com.c0dex.passvault.data.VaultRepository
import com.c0dex.passvault.session.ClipboardHelper
import com.c0dex.passvault.session.SessionManager
import com.c0dex.passvault.ui.lock.LockScreen
import com.c0dex.passvault.ui.settings.SettingsScreen
import com.c0dex.passvault.ui.theme.PassVaultTheme
import com.c0dex.passvault.ui.vault.DetailScreen
import com.c0dex.passvault.ui.vault.VaultListScreen

/**
 * Single Activity + гейт по состоянию сессии (D-004).
 * LOCKED -> только лок-скрин; UNLOCKED -> NavHost (список / деталь / настройки).
 * Любое касание = активность (сброс idle-таймера).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        val prefs = SettingsPrefs(this)
        val store = VaultFileStore(this)
        val session = SessionManager(this, store, prefs, lifecycleScope)
        val repo = VaultRepository(session)
        val clipboard = ClipboardHelper(this, prefs, lifecycleScope)

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

                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            session.touch()
                        }
                    }
                ) {
                    if (state is SessionManager.State.Locked) {
                        LockScreen(session, store, prefs)
                    } else {
                        val nav = rememberNavController()
                        NavHost(navController = nav, startDestination = "list") {
                            composable("list") {
                                VaultListScreen(
                                    session, repo,
                                    onOpen = { id -> nav.navigate("detail/$id") },
                                    onSettings = { nav.navigate("settings") }
                                )
                            }
                            composable("detail/{id}") { entry ->
                                DetailScreen(
                                    id = entry.arguments?.getString("id") ?: "",
                                    repo = repo,
                                    clipboard = clipboard,
                                    onBack = { nav.popBackStack() }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(session, store, prefs, onBack = { nav.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}