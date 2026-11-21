package com.palabrasfuncionales.app

import com.palabrasfuncionales.app.net.OnlineTranslator
import com.palabrasfuncionales.app.vocab.VocabItem
import com.palabrasfuncionales.app.vocab.VocabRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineParseTest {
    @Test
    fun parsesTranslationPronunciationAndDetectedLanguage() {
        val t = OnlineTranslator.parse("""[[["مرحبا","hello",null,null,10],[null,null,"marhaban"]],null,"en"]""")
        assertEquals("مرحبا", t.text)
        assertEquals("marhaban", t.translit)
        assertNull(t.sourceTranslit)
        assertEquals("en", t.detectedSource)
    }

    @Test
    fun joinsSegmentsAndReadsSourcePronunciation() {
        val t = OnlineTranslator.parse(
            """[[["Hello. ","こんにちは。",null,null,3],["Bye","さようなら",null,null,3],[null,null,null,"Kon'nichiwa. Sayōnara"]],null,"ja"]"""
        )
        assertEquals("Hello. Bye", t.text)
        assertNull(t.translit)
        assertEquals("Kon'nichiwa. Sayōnara", t.sourceTranslit)
        assertEquals("ja", t.detectedSource)
    }

    @Test
    fun norwegianGetsIndefiniteArticle() {
        val item = VocabItem(0, "1. X", "la madre", "the mother")
        assertEquals("a mother", VocabRepository.mtSource(item, "no"))
        assertEquals("the mother", VocabRepository.mtSource(item, "de"))
        assertEquals("mother", item.enShown)
    }
}
