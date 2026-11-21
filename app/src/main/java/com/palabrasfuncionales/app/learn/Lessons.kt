package com.palabrasfuncionales.app.learn

import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.data.LanguagePack

/** A bite-sized part of one chapter of the guide. */
data class Lesson(
    val key: String,
    val section: String,
    val part: Int,
    val parts: Int,
    val entries: List<Entry>,
)

object Lessons {
    /** Working memory holds about 7 items, so lessons stay small. */
    const val SIZE = 8

    /** Chapters in book order, each split into balanced parts of at most [SIZE] words. */
    fun build(pack: LanguagePack): List<Lesson> {
        val out = ArrayList<Lesson>()
        for (section in pack.sections) {
            val list = pack.entries.filter { it.section == section }
            if (list.isEmpty()) continue
            val parts = (list.size + SIZE - 1) / SIZE
            val base = list.size / parts
            val extra = list.size % parts
            var from = 0
            for (p in 0 until parts) {
                val n = base + if (p < extra) 1 else 0
                out += Lesson("$section#${p + 1}", section, p + 1, parts, list.subList(from, from + n))
                from += n
            }
        }
        return out
    }
}
