package com.formready.resizer.core.pdf

/** PDF points, 72 per inch. */
enum class PageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    ORIGINAL("Original image size", 0, 0)
}

enum class PageOrientation(val label: String) { PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

enum class PdfQuality(val label: String, val jpegQuality: Int, val maxEdgePx: Int) {
    HIGH("High quality", 92, 3000),
    BALANCED("Balanced", 78, 2200),
    SMALL("Smallest file", 60, 1600)
}
