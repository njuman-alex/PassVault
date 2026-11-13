package com.c0dex.core.merge

import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.RecordEnvelope
import com.c0dex.core.format.VaultContainer

/**
 * Слияние контейнеров PMV3 (FORMAT.md §5).
 *
 * Правила (детерминированные и симметричные):
 *  - объединение id: запись, присутствующая только с одной стороны, переносится;
 *  - по каждому id побеждает больший updatedAt;
 *  - при равном updatedAt tombstone побеждает живую запись;
 *  - при полном равенстве — лексикографически больший ct (симметричный tie-break);
 *  - нечитаемая (повреждённая) запись уступает читаемой; обе нечитаемы — сохраняем локальную;
 *  - результат отсортирован по id (стабильный порядок байтов файла);
 *  - заголовок (kdf/wraps) берётся из локального контейнера `a`:
 *    ротация мастера выполняется на одном устройстве за раз (v1, вне scope слияния).
 *
 * Слияние выполняется ПОСЛЕ разблокировки (DEK в памяти): updatedAt живёт внутри шифра.
 */
object VaultMerger {

    fun merge(dek: ByteArray, a: VaultContainer, b: VaultContainer): VaultContainer =
        VaultContainer(a.kdf, a.wraps, mergeRecords(dek, a.records, b.records))

    fun mergeRecords(dek: ByteArray, a: List<RecordEnvelope>, b: List<RecordEnvelope>): List<RecordEnvelope> {
        val map = LinkedHashMap<String, RecordEnvelope>()
        for (e in a) map[e.id] = e
        for (e in b) {
            val cur = map[e.id]
            map[e.id] = if (cur == null) e else winner(dek, cur, e)
        }
        return map.values.sortedBy { it.id }
    }

    private fun winner(dek: ByteArray, x: RecordEnvelope, y: RecordEnvelope): RecordEnvelope {
        val px = ContainerCodec.openPayload(dek, x)
        val py = ContainerCodec.openPayload(dek, y)
        if (px == null && py == null) return x
        if (px == null) return y
        if (py == null) return x
        if (px.updatedAt != py.updatedAt) return if (px.updatedAt > py.updatedAt) x else y
        if (px.deleted != py.deleted) return if (px.deleted) x else y
        return if (compareUnsigned(x.ct, y.ct) >= 0) x else y
    }

    private fun compareUnsigned(x: ByteArray, y: ByteArray): Int {
        val n = minOf(x.size, y.size)
        for (i in 0 until n) {
            val d = (x[i].toInt() and 0xFF) - (y[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return x.size - y.size
    }
}