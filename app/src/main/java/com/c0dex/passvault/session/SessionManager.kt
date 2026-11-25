package com.c0dex.passvault.session

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
    // было: private var externalActivity = false
    private var expectPickerStop = false   // D-023: ждём ровно один ON_STOP от нашего SAF-пикера
    // было: private var pickerReturned = false     // D-023: результат пикера доставлен (выбор ИЛИ отмена)
    private var pickerFlow = false   // D-023-fix: ON_STOP от пикера пойман, ждём возврат

    private companion object {
        const val PICKER_FLOW_MAX_MS = 10 * 60_000L   // кап против «HOME из пикера надолго»
    }

    /** Доступ к DEK только в UNLOCKED; вызывающий не сохраняет и не затирает массив. */
    fun <T> withDek(block: (ByteArray) -> T): T? = dek?.let(block)

    fun currentContainer(): VaultContainer? = container

    // ===== разблокировка / создание =====

    fun unlock(uri: Uri, master: CharArray): UnlockResult {
        val now = System.currentTimeMillis()
        val until = prefs.failUntilTs
        if (now < until) { master.fill('\u0000'); return UnlockResult.Delayed(until - now)}
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
                prefs.failUntilTs = System.currentTimeMillis() + delayFor(prefs.failCount)
                return UnlockResult.WrongMaster
            }
            dek = d
            container = c
            prefs.failCount = 0
            prefs.failUntilTs = 0
            prefs.vaultUri = uri.toString()
            _state.value = State.Unlocked   // состояние ДО запуска тикера
            touch()
            startIdle()
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
            prefs.failUntilTs = 0
            _state.value = State.Unlocked   // состояние ДО запуска тикера
            touch()
            startIdle()
            return true
        } finally {
            master.fill('\u0000')
        }
    }

    // ===== лок =====

    fun lock() {
        // было: только dek/container/idleJob/state
        expectPickerStop = false   // D-023-fix: лок сбрасывает висячие флаги пикера
        pickerFlow = false
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

    /** Тикер: сначала delay, потом проверка — не зависит от состояния в момент старта. */
    private fun startIdle() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (true) {
                delay(1000)
                if (!isUnlocked) break
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

    /** Записывает новый контейнер и обновляет сессию (для CRUD записей). */
    fun replaceContainer(c: VaultContainer): Boolean {
        val uri = prefs.vaultUri?.let(Uri::parse) ?: return false
        if (!store.write(uri, ContainerCodec.serialize(c))) return false
        container = c
        touch()
        return true
    }

    // ===== внешние activity (SAF picker и т.п.): не считать уходом в фон =====

    // D-023: узкое исключение — только наш SAF-пикер, ровно на один ON_STOP
    fun beginExternalActivity() { expectPickerStop = true }

    // D-023: зовётся из callback лаунчера (выбор И отмена); заменяет endExternalActivity
    // было: fun notePickerReturned() { pickerReturned = true }
    // D-023-fix: callback больше не участвует в решении (ON_START раньше onActivityResult)

    // было:
    //    fun endExternalActivity() {
    //        externalActivity = false
    //        if (isUnlocked) { touch(); startIdle() }
    //    }

    fun onBackground() {
        backgroundTs = System.currentTimeMillis()
        // было: if (expectPickerStop) { expectPickerStop = false; return }   // пикер открылся: не лочим, ts фиксируем
        if (expectPickerStop) { expectPickerStop = false; pickerFlow = true; return }   // пикер открылся
        // D-023: «сразу» (grace=0) = лок В МОМЕНТ ухода, DEK затирается немедленно
        if (prefs.backgroundGraceMs == 0L) lock()
    }


    fun onForeground() {
        if (expectPickerStop) expectPickerStop = false   // пикер так и не открылся — сброс ожидания
        if (!isUnlocked) return
        // было: if (pickerReturned) { pickerReturned = false; touch(); startIdle(); return }   // D-023
        if (pickerFlow) {
            pickerFlow = false
            // возврат из пикера: не лочим (callback merge ещё впереди); кап 10 мин закрывает HOME-из-пикера
            if (System.currentTimeMillis() - backgroundTs <= PICKER_FLOW_MAX_MS) { touch(); startIdle(); return }
        }
        val grace = prefs.backgroundGraceMs
        if (System.currentTimeMillis() - backgroundTs > grace) lock()
        else { touch(); startIdle() }
    }
}