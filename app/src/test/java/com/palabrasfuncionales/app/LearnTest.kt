package com.palabrasfuncionales.app

import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.data.LanguagePack
import com.palabrasfuncionales.app.data.Languages
import com.palabrasfuncionales.app.learn.CardState
import com.palabrasfuncionales.app.learn.Lessons
import com.palabrasfuncionales.app.learn.Quiz
import com.palabrasfuncionales.app.learn.Srs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearnTest {
    private fun e(id: Int, w: String, es: String, sec: String = "1. Test") =
        Entry(id, w, null, null, es, null, null, null, null, sec)

    @Test
    fun leitnerBoxesMoveUpAndFallBack() {
        assertEquals(CardState(2, 102), Srs.review(null, true, 100))
        assertEquals(CardState(1, 101), Srs.review(null, false, 100))
        assertEquals(CardState(3, 106), Srs.review(CardState(2, 102), true, 102))
        assertEquals(CardState(5, 26), Srs.review(CardState(5, 0), true, 10))
        assertEquals(CardState(1, 11), Srs.review(CardState(4, 0), false, 10))
        assertTrue(Srs.isDue(CardState(1, 10), 10))
        assertFalse(Srs.isDue(CardState(1, 11), 10))
        assertFalse(Srs.isDue(null, 10))
    }

    @Test
    fun lessonsAreSmallBalancedAndInBookOrder() {
        val list = (0 until 19).map { e(it, "w$it", "m$it", "1. A") } +
            (0 until 3).map { e(19 + it, "g$it", "gm$it", LanguagePack.GLOSSARY) }
        val lessons = Lessons.build(LanguagePack(Languages.byCode("no")!!, list))
        assertEquals(listOf(7, 6, 6, 3), lessons.map { it.entries.size })
        assertEquals(LanguagePack.GLOSSARY, lessons.last().section)
        assertEquals(22, lessons.sumOf { it.entries.size })
    }

    @Test
    fun quizHasFourDistinctOptionsAndNoAmbiguousDistractor() {
        val pool = listOf(
            e(0, "aber", "pero"), e(1, "sondern", "sino; pero"), e(2, "und", "y"),
            e(3, "oder", "o"), e(4, "denn", "pues, porque"), e(5, "weil", "porque"),
        )
        repeat(100) { i ->
            val q = Quiz.make(pool[0], pool, reverse = i % 2 == 1, rnd = Random(i))
            assertEquals(4, q.options.distinct().size)
            assertEquals(Quiz.label(pool[0], q.reverse), q.options[q.answer])
            if (!q.reverse) assertFalse("sino; pero" in q.options)
        }
    }
}
