package com.palabrasfuncionales.app.util

import java.text.Normalizer
import java.util.Locale

/** Accent-, case- and diacritic-insensitive folding used for searching. */
object TextNorm {
    private val combiningMarks = Regex("\\p{Mn}+")
    // Transliteration apostrophes (ʿ ʾ), Arabic tatweel and quote marks.
    private val ignorable = Regex("[ʿʾʼ'’‘`´ـ]")
    private val spaces = Regex("\\s+")

    fun fold(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed
            .replace(combiningMarks, "")
            .replace(ignorable, "")
            .replace('ß', 's')
            .lowercase(Locale.ROOT)
            .replace(spaces, " ")
            .trim()
    }
}
