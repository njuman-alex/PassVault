package com.c0dex.passvault.data

import android.net.Uri
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.merge.VaultMerger
import com.c0dex.passvault.session.SessionManager

/**
 * Синк v1 (FORMAT.md §5): ручное слияние конфликт-копии облака.
 * Только в UNLOCKED (DEK в памяти); правила слияния — VaultMerger.
 * Автодетект конфликт-копий — позже (amendment).
 */
class SyncHelper(
    private val session: SessionManager,
    private val store: VaultFileStore
) {
    sealed class Result {
        object Ok : Result()
        object Locked : Result()
        object ReadFail : Result()
        object ParseFail : Result()
        object WriteFail : Result()
    }

    fun mergeConflict(other: Uri): Result {
        if (!session.isUnlocked) return Result.Locked
        val base = session.currentContainer() ?: return Result.Locked
        val text = store.read(other) ?: return Result.ReadFail
        val otherC = try {
            ContainerCodec.parse(text)
        } catch (e: Exception) {
            return Result.ParseFail
        }
        val merged = session.withDek { dek -> VaultMerger.merge(dek, base, otherC) }
            ?: return Result.Locked
        return if (session.replaceContainer(merged)) Result.Ok else Result.WriteFail
    }
}