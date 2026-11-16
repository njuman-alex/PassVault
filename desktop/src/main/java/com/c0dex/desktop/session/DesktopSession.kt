package com.c0dex.desktop.session

import com.c0dex.desktop.data.DesktopPrefs
import com.c0dex.desktop.io.VaultFileIO
import com.c0dex.core.crypto.Argon2Kdf
import com.c0dex.core.crypto.KeyHierarchy
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.KdfConfig
import com.c0dex.core.format.VaultContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Сессия десктопа: порт SESSION.md без Android (фон = потеря фокуса окна). */
class DesktopSession(private val scope: CoroutineScope, private val prefs: DesktopPrefs) {

    sealed class State { object Locked : State(); object Unlocked : State() }
    sealed class UnlockResult {
        object Success : UnlockResult(); object WrongMaster : UnlockResult()
        object CorruptFile : UnlockResult(); object IoError : UnlockResult()
        data class Delayed(val remainingMs: Long) : UnlockResult()
    }

    private val _state = MutableStateFlow<State>(State.Locked)
    val state: StateFlow<State> = _state.asStateFlow()
    val isUnlocked get() = _state.value is State.Unlocked

    private var dek: ByteArray? = null
    private var container: VaultContainer? = null
    private var idleJob: Job? = null
    private var backgroundTs = 0L
    private var failUntil = 0L

    fun <T> withDek(block: (ByteArray) -> T): T? = dek?.let(block)
    fun currentContainer(): VaultContainer? = container

    fun unlock(path: Path, master: CharArray): UnlockResult {
        val now = System.currentTimeMillis()
        if (now < failUntil) return UnlockResult.Delayed(failUntil - now)
        try {
            val text = VaultFileIO.read(path) ?: return UnlockResult.IoError
            val c = try { ContainerCodec.parse(text) } catch (e: Exception) { return UnlockResult.CorruptFile }
            val wrap = c.wraps.firstOrNull { it.type == KeyHierarchy.WRAP_TYPE_MASTER } ?: return UnlockResult.CorruptFile
            val kek = Argon2Kdf.derive(master, c.kdf.toParams())
            val d = KeyHierarchy.unwrapDek(wrap, kek)
            kek.fill(0)
            if (d == null) {
                prefs.failCount = prefs.failCount + 1
                failUntil = System.currentTimeMillis() + delayFor(prefs.failCount)
                return UnlockResult.WrongMaster
            }
            dek = d; container = c
            prefs.failCount = 0; prefs.vaultPath = path.toString()
            _state.value = State.Unlocked
            touch(); startIdle()
            return UnlockResult.Success
        } finally { master.fill('\u0000') }
    }

    /** null = успех, иначе причина для UI. */
    fun create(path: Path, master: CharArray): String? {
        try {
            val salt = Argon2Kdf.newSalt()
            val params = Argon2Kdf.Params(salt = salt)
            val kek = Argon2Kdf.derive(master, params)
            val d = KeyHierarchy.newDek()
            val wrap = KeyHierarchy.wrapDek(d, kek)
            kek.fill(0)
            val c = VaultContainer(KdfConfig(params.memoryKiB, params.iterations, params.parallelism, salt), listOf(wrap), emptyList())
            val err = VaultFileIO.writeAtomicMsg(path, ContainerCodec.serialize(c))
            if (err != null) return err
            dek = d; container = c
            prefs.vaultPath = path.toString(); prefs.failCount = 0
            _state.value = State.Unlocked
            touch(); startIdle()
            return null
        } finally { master.fill('\u0000') }
    }

    fun lock() {
        dek?.fill(0); dek = null; container = null
        idleJob?.cancel(); idleJob = null
        _state.value = State.Locked
    }

    fun replaceContainer(c: VaultContainer): Boolean {
        val p = prefs.vaultPath?.let(Path::of) ?: return false
        if (!VaultFileIO.writeAtomic(p, ContainerCodec.serialize(c))) return false
        container = c; touch(); return true
    }

    fun rotateMaster(newMaster: CharArray): Boolean {
        val d = dek ?: return false
        val c = container ?: return false
        val p = prefs.vaultPath?.let(Path::of) ?: return false
        try {
            val salt = Argon2Kdf.newSalt()
            val params = Argon2Kdf.Params(salt = salt)
            val kek = Argon2Kdf.derive(newMaster, params)
            val wrap = KeyHierarchy.wrapDek(d, kek)
            kek.fill(0)
            val newC = c.copy(
                kdf = KdfConfig(params.memoryKiB, params.iterations, params.parallelism, salt),
                wraps = c.wraps.map { if (it.type == KeyHierarchy.WRAP_TYPE_MASTER) wrap else it }
            )
            if (!VaultFileIO.writeAtomic(p, ContainerCodec.serialize(newC))) return false
            container = newC; return true
        } finally { newMaster.fill('\u0000') }
    }

    fun touch() { prefs.lastActiveTs = System.currentTimeMillis() }

    private fun startIdle() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (true) {
                delay(1000)
                if (!isUnlocked) break
                val idle = prefs.idleMs
                if (idle > 0 && System.currentTimeMillis() - prefs.lastActiveTs > idle) lock()
            }
        }
    }

    fun onFocusLost() { backgroundTs = System.currentTimeMillis() }

    fun onFocusGained() {
        if (!isUnlocked) return
        if (System.currentTimeMillis() - backgroundTs > prefs.graceMs) lock()
        else { touch(); startIdle() }
    }

    private fun delayFor(fails: Int): Long = when {
        fails >= 20 -> 300_000L; fails >= 10 -> 30_000L; fails >= 5 -> 5_000L; else -> 0L
    }
}