package com.formready.resizer.core.pdf

/**
 * Parses page selections the way people write them: "1-3, 5, 8-10".
 * Returns 1-based page numbers, sorted and de-duplicated, clamped to [pageCount].
 */
object PageRanges {

    fun parse(input: String, pageCount: Int): List<Int> {
        if (input.isBlank()) return emptyList()
        val pages = sortedSetOf<Int>()

        input.split(',', ' ', '\n').filter { it.isNotBlank() }.forEach { chunk ->
            val part = chunk.trim()
            when {
                part.contains('-') -> {
                    val bounds = part.split('-')
                    val start = bounds.getOrNull(0)?.trim()?.toIntOrNull()
                    val end = bounds.getOrNull(1)?.trim()?.toIntOrNull()
                    if (start != null && end != null) {
                        (minOf(start, end)..maxOf(start, end)).forEach { pages += it }
                    }
                }
                else -> part.toIntOrNull()?.let { pages += it }
            }
        }
        return pages.filter { it in 1..pageCount }
    }

    fun describe(pages: List<Int>): String = when {
        pages.isEmpty() -> "none"
        else -> pages.joinToString(", ")
    }
}
