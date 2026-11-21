package com.palabrasfuncionales.app.learn

import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.util.TextNorm
import kotlin.random.Random

/**
 * One multiple-choice question. Forward: see the word, pick the Spanish
 * meaning. Reverse: see the Spanish, pick the word (harder: active recall).
 */
data class Question(
    val entry: Entry,
    val reverse: Boolean,
    val options: List<String>,
    val answer: Int,
)

object Quiz {
    fun label(e: Entry, reverse: Boolean): String =
        if (!reverse) e.spanish
        else if (!e.translit.isNullOrBlank()) "${e.word}  (${e.translit})"
        else e.word

    /** Distractors come from the same chapter first, so the choice is a real test. */
    fun make(entry: Entry, pool: List<Entry>, reverse: Boolean, rnd: Random): Question {
        val correct = label(entry, reverse)
        val c = TextNorm.fold(if (reverse) entry.word else entry.spanish)
        val sameSection = pool.filter { it.section == entry.section && it != entry }.shuffled(rnd)
        val others = pool.filter { it.section != entry.section }.shuffled(rnd)
        val distractors = (sameSection + others).asSequence()
            .filter {
                val f = TextNorm.fold(if (reverse) it.word else it.spanish)
                f.isNotEmpty() && !f.contains(c) && !c.contains(f)
            }
            .map { label(it, reverse) }
            .distinct()
            .take(3)
            .toList()
        val options = (distractors + correct).shuffled(rnd)
        return Question(entry, reverse, options, options.indexOf(correct))
    }
}
