package com.palabrasfuncionales.app

import com.palabrasfuncionales.app.util.Romanizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomanizerTest {
    @Test
    fun russian() = assertEquals("Privet, kak dela?", Romanizer.cyrillic("Привет, как дела?"))

    @Test
    fun arabicApproximate() {
        assertEquals("marhaban", Romanizer.arabic("مَرْحَبًا"))
        assertEquals("al-", Romanizer.arabic("الكتاب").take(3))
    }

    @Test
    fun japaneseKana() {
        assertEquals("kippu", Romanizer.kana("きっぷ"))
        assertEquals("koohii", Romanizer.kana("コーヒー"))
        assertEquals("matchi", Romanizer.kana("マッチ"))
    }

    @Test
    fun latinLanguagesNeedNothing() = assertNull(Romanizer.romanize("hola", "es"))
}
