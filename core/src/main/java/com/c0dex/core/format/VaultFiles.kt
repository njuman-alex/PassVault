package com.c0dex.core.format

/**
 * Единая точка правды по расширениям и именам сейф-файла.
 *
 * ВАЖНО: расширение файла (.qdex) — косметика ОС, не связано с wire-форматом.
 * Формат контейнера называется PMV3 (PassVault v3), magic-байты = "PMV3" внутри файла.
 * Формат остаётся PMV3 навсегда; расширение может меняться для брендинга.
 */
object VaultFiles {
    const val EXT = "qdex"
    const val DEFAULT_NAME = "vault.qdex"
    const val TEMP_PREFIX = ".qdex-"
}