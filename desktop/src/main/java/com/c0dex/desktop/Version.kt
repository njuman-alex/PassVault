package com.c0dex.desktop

/**
 * Версия десктоп-приложения. Бампается ВРУЧНУЮ при изменениях десктопа,
 * независимо от Android (у того свой versionName в :app).
 * packageVersion в gradle — отдельная сущность для установщика, бампай по необходимости.
 */
object Version {
    const val NAME = "5.7.1"
}