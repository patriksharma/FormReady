package com.formready.resizer.core.util

import java.util.Locale

/** Human readable size. Uses KB = 1024 bytes, which is what form portals mean in practice. */
fun Long.formatSize(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", this / 1024.0)
    else -> String.format(Locale.US, "%.2f MB", this / (1024.0 * 1024.0))
}

fun Int.formatSize(): String = toLong().formatSize()

fun kbToBytes(kb: Int): Long = kb.toLong() * 1024L
