package com.c0dex.passvault.session

import android.content.Context
import android.net.Uri
import com.c0dex.core.crypto.Argon2Kdf
import com.c0dex.core.crypto.KeyHierarchy
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.KdfConfig
import com.c0dex.core.format.VaultContainer
import com.c0dex.passvault.data.SettingsPrefs
import com.c0dex.passvault.data.VaultFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Сессия и лок-модель (SESSION.md).
 *
 * Контракт:
 *  - DEK и контейнер живут ТОЛЬКО в памяти; при локе DEK затирается;
 *  - мастер нигде не сохраняется;
 *  - автолок: idle-таймер + уход в фон (grace) + ручной лок;
 *  - анти-брутфорс: прогрессивная задержка по счётчику неудач (prefs, не секрет);
 *  - ротация мастера переобёртывает текущий DEK, записи не трогаются.
 */
class SessionManager(
    private val context: Context,
    private val store: VaultFileStore,
    private val prefs: SettingsPrefs,
    private val scope: CoroutineScope
) {

    sealed class State {
        object Locked : State()
        object Unlocked : State()
    }

    sealed class UnlockResult {
        object Success : UnlockResult()
        object WrongMaster : UnlockResult()
        object CorruptFile : UnlockResult()
        object IoError : UnlockResult()
        data class Delayed(val remainingMs: Long) : UnlockResult()
    }

    private val _state = MutableStateFlow<State>(State.Locked)
    val state: StateFlow<State> = _state.asStateFlow()
    val isUnlocked: Boolean get() = _state.value is State.Unlocked

    private var dek: ByteArray? = null
    private var container: VaultContainer? = null
    private var idleJob: Job? = null
    private var backgroundTs = 0L
    private var failUntil = 0L

    /** Доступ к DEK только в UNLOCKED; вызывающий не сохраняет и не затирает массив. */
    fun <T> withDek(block: (ByteArray) -> T): T? = dek?.let(block)

    fun currentContainer(): VaultContainer? = container

    // ===== разблокировка / создание =====

    fun unlock(uri: Uri, master: CharArray): UnlockResult {
        val now = System.currentTimeMillis()
        if (now < failUntil) return UnlockResult.Delayed(failUntil - now)
        try {
            val text = store.read(uri) ?: return UnlockResult.IoError
            val c = try {
                ContainerCodec.parse(text)
            } catch (e: Exception) {
                return UnlockResult.CorruptFile
            }
            val wrap = c.wraps.firstOrNull { it.type == KeyHierarchy.WRAP_TYPE_MASTER }
                ?: return UnlockResult.CorruptFile
            val kek = Argon2Kdf.derive(master, c.kdf.toParams())
            val d = KeyHierarchy.unwrapDek(wrap, kek)
            kek.fill(0)
            if (d == null) {
                prefs.failCount = prefs.failCount + 1
                failUntil = System.currentTimeMillis() + delayFor(prefs.failCount)
                return UnlockResult.WrongMaster
            }
            dek = d
            container = c
            prefs.failCount = 0
            prefs.vaultUri = uri.toString()
            touch()
            startIdle()
            _state.value = State.Unlocked
            return UnlockResult.Success
        } finally {
            master.fill('\u0000')
        }
    }

    fun create(uri: Uri, master: CharArray): Boolean {
        try {
            val salt = Argon2Kdf.newSalt()
            val params = Argon2Kdf.Params(salt = salt)
            val kek = Argon2Kdf.derive(master, params)
            val d = KeyHierarchy.newDek()
            val wrap = KeyHierarchy.wrapDek(d, kek)
            kek.fill(0)
            val c = VaultContainer(
                KdfConfig(params.memoryKiB, params.iterations, params.parallelism, salt),
                listOf(wrap),
                emptyList()
            )
            if (!store.write(uri, ContainerCodec.serialize(c))) return false
            dek = d
            container = c
            prefs.vaultUri = uri.toString()
            prefs.failCount = 0
            touch()
            startIdle()
            _state.value = State.Unlocked
            return true
        } finally {
            master.fill('\u0000')
        }
    }

    // ===== лок =====

    fun lock() {
        dek?.fill(0)
        dek = null
        container = null
        idleJob?.cancel()
        idleJob = null
        _state.value = State.Locked
    }

    // ===== ротация мастера (переобёртка текущего DEK) =====

    fun rotateMaster(newMaster: CharArray): Boolean {
        val d = dek ?: return false
        val c = container ?: return false
        val uri = prefs.vaultUri?.let(Uri::parse) ?: return false
        try {
            val salt = Argon2Kdf.newSalt()
            val params = Argon2Kdf.Params(salt = salt)
            val kek = Argon2Kdf.derive(newMaster, params)
            val newWrap = KeyHierarchy.wrapDek(d, kek)
            kek.fill(0)
            val newC = c.copy(
                kdf = KdfConfig(params.memoryKiB, params.iterations, params.parallelism, salt),
                wraps = c.wraps.map { if (it.type == KeyHierarchy.WRAP_TYPE_MASTER) newWrap else it }
            )
            if (!store.write(uri, ContainerCodec.serialize(newC))) return false
            container = newC
            return true
        } finally {
            newMaster.fill('\u0000')
        }
    }

    // ===== автолок =====

    fun touch() {
        prefs.lastActiveTs = System.currentTimeMillis()
    }

    fun checkIdle() {
        if (!isUnlocked) return
        val idle = prefs.idleTimeoutMs
        if (idle > 0 && System.currentTimeMillis() - prefs.lastActiveTs > idle) lock()
    }

    fun onBackground() {
        backgroundTs = System.currentTimeMillis()
    }

    fun onForeground() {
        if (!isUnlocked) return
        val grace = prefs.backgroundGraceMs
        if (System.currentTimeMillis() - backgroundTs > grace) lock()
        else { touch(); startIdle() }
    }

    private fun startIdle() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isUnlocked) {
                delay(1000)
                checkIdle()
            }
        }
    }

    private fun delayFor(fails: Int): Long = when {
        fails >= 20 -> 5 * 60_000L
        fails >= 10 -> 30_000L
        fails >= 5 -> 5_000L
        else -> 0L
    }
}