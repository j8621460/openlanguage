package com.palabrasfuncionales.app

import com.palabrasfuncionales.app.data.Search
import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.data.LanguagePack
import com.palabrasfuncionales.app.data.Languages
import com.palabrasfuncionales.app.util.TextNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private fun entry(id: Int, word: String, es: String, tr: String? = null, sec: String = "1. Test") =
        Entry(id, word, tr, null, es, null, null, null, null, sec)

    @Test
    fun foldIgnoresAccentsCaseAndMarks() {
        assertEquals("aun", TextNorm.fold("Aún"))
        assertEquals("dhalika", TextNorm.fold("dhālika"))
        assertEquals("ala", TextNorm.fold("ʿalā"))
        assertEquals("еще", TextNorm.fold("ЕЩЁ"))
        assertEquals(TextNorm.fold("انت"), TextNorm.fold("أنتَ"))
    }

    @Test
    fun exactHeadwordRanksFirst() {
        val pack = LanguagePack(
            Languages.byCode("de")!!,
            listOf(entry(0, "abends", "por la tarde"), entry(1, "aber", "pero"), entry(2, "sondern", "sino; pero"))
        )
        val r = Search.run(pack, "aber", null)
        assertEquals("aber", r.first().word)
    }

    @Test
    fun spanishMeaningIsSearchable() {
        val pack = LanguagePack(
            Languages.byCode("fr")!!,
            listOf(entry(0, "mais", "pero"), entry(1, "pourtant", "sin embargo"))
        )
        val r = Search.run(pack, "EMBARGO", null)
        assertEquals(listOf("pourtant"), r.map { it.word })
    }

    @Test
    fun sectionFilterAndGlossaryLast() {
        val pack = LanguagePack(
            Languages.byCode("it")!!,
            listOf(
                entry(0, "ma", "pero", sec = LanguagePack.GLOSSARY),
                entry(1, "di", "de", sec = "2. Preposiciones"),
                entry(2, "il", "el", sec = "1. Artículos"),
            )
        )
        assertEquals(listOf("1. Artículos", "2. Preposiciones", LanguagePack.GLOSSARY), pack.sections)
        assertTrue(Search.run(pack, "", "2. Preposiciones").all { it.section == "2. Preposiciones" })
    }
}
