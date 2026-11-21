package com.palabrasfuncionales.app.learn

import java.time.LocalDate

/** Progress of one card: its Leitner box and the day it is due again. */
data class CardState(val box: Int, val dueDay: Long)

/**
 * Spaced repetition with Leitner boxes. Right answer: the card moves up a box
 * and comes back later. Wrong answer: back to box 1 (tomorrow). Reviewing
 * right before you would forget is what makes memories stick.
 */
object Srs {
    const val MAX_BOX = 5
    const val MASTERED_BOX = 4
    /** Days until the next review, indexed by box. */
    private val INTERVALS = intArrayOf(0, 1, 2, 4, 8, 16)

    fun interval(box: Int): Int = INTERVALS[box.coerceIn(1, MAX_BOX)]

    fun review(previous: CardState?, correct: Boolean, today: Long): CardState {
        val box = if (correct) ((previous?.box ?: 1) + 1).coerceAtMost(MAX_BOX) else 1
        return CardState(box, today + interval(box))
    }

    fun isDue(state: CardState?, today: Long): Boolean = state != null && state.dueDay <= today

    fun today(): Long = LocalDate.now().toEpochDay()
}
