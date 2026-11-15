package com.c0dex.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Human-readable время для UI (epoch millis UTC -> локальная зона устройства).
 * Pure JVM: переиспользуется Android и Windows (D-007).
 */
object TimeFormat {

    private val FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

    fun human(ms: Long): String = FMT.format(Instant.ofEpochMilli(ms))
}