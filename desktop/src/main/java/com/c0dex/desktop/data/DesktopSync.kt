package com.c0dex.desktop.data

import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.merge.VaultMerger
import com.c0dex.desktop.io.VaultFileIO
import com.c0dex.desktop.session.DesktopSession
import java.nio.file.Path

/** Синк v1 десктопа (зеркало Android SyncHelper). Только в UNLOCKED. */
class DesktopSync(private val session: DesktopSession) {
    sealed class Result {
        object Ok : Result(); object Locked : Result(); object ReadFail : Result()
        object ParseFail : Result(); object ForeignFile : Result(); object WriteFail : Result()
    }

    fun mergeConflict(other: Path): Result {
        if (!session.isUnlocked) return Result.Locked
        val base = session.currentContainer() ?: return Result.Locked
        val text = VaultFileIO.read(other) ?: return Result.ReadFail
        val otherC = try { ContainerCodec.parse(text) } catch (e: Exception) { return Result.ParseFail }
        val foreign = session.withDek { dek -> VaultMerger.isForeign(dek, otherC) } ?: return Result.Locked
        if (foreign) return Result.ForeignFile
        val merged = session.withDek { dek -> VaultMerger.merge(dek, base, otherC) } ?: return Result.Locked
        return if (session.replaceContainer(merged)) Result.Ok else Result.WriteFail
    }
}