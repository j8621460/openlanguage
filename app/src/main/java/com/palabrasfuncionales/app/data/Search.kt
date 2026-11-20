package com.palabrasfuncionales.app.data

import com.palabrasfuncionales.app.util.TextNorm

/** Pure-Kotlin search, kept free of Android classes so it is unit-testable. */
object Search {
    /**
     * Ranked search over headword, transliteration, Spanish meaning and example.
     * Ignores case, accents and Arabic vowel marks.
     */
    fun run(pack: LanguagePack, query: String, section: String?): List<Entry> {
        val base = if (section == null) pack.entries else pack.entries.filter { it.section == section }
        val q = TextNorm.fold(query)
        if (q.isEmpty()) return base
        return base.asSequence()
            .map { it to score(it, q) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }

    internal fun score(e: Entry, q: String): Int {
        val w = e.wordKey
        return when {
            w == q -> 100
            w.split(' ', '/', ',', ';').any { it.trim() == q } -> 90
            w.startsWith(q) -> 80
            w.contains(q) -> 60
            e.spanishKey.split(' ', '/', ',', ';', '(', ')').any { it.trim() == q } -> 50
            e.spanishKey.contains(q) -> 40
            e.exampleKey.contains(q) -> 10
            else -> 0
        }
    }
}
