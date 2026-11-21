package com.palabrasfuncionales.app.data

import com.palabrasfuncionales.app.util.TextNorm

data class Lang(
    val code: String,      // Google Translate / ISO 639-1 code
    val english: String,
    val native: String,
    val ttsTag: String,
) {
    val label: String get() = if (english == native) english else "$english ($native)"
}

object Languages {
    val dictionary: List<Lang> = listOf(
        Lang("de", "German", "Deutsch", "de-DE"),
        Lang("fr", "French", "Français", "fr-FR"),
        Lang("it", "Italian", "Italiano", "it-IT"),
        Lang("pt", "Portuguese", "Português", "pt-PT"),
        Lang("nl", "Dutch", "Nederlands", "nl-NL"),
        Lang("ru", "Russian", "Русский", "ru-RU"),
        Lang("ar", "Arabic", "العربية", "ar"),
        Lang("ga", "Irish", "Gaeilge", "ga-IE"),
        Lang("no", "Norwegian", "Norsk", "nb-NO"),
        Lang("yo", "Yoruba", "Yorùbá", "yo-NG"),
        Lang("sw", "Swahili", "Kiswahili", "sw-KE"),
        Lang("zu", "Zulu", "isiZulu", "zu-ZA"),
        Lang("ja", "Japanese", "日本語", "ja-JP"),
    )
    val spanish = Lang("es", "Spanish", "Español", "es-ES")
    val english = Lang("en", "English", "English", "en-US")

    /** Every language the translator offers as a target. */
    val all: List<Lang> = listOf(english, spanish) + dictionary

    fun byCode(code: String?): Lang? = all.firstOrNull { it.code == code }
}

data class Entry(
    val id: Int,
    val word: String,
    val translit: String?,
    val pron: String?,
    val spanish: String,
    val example: String?,
    val exampleTranslit: String?,
    val exampleSpanish: String?,
    val note: String?,
    val section: String,
) {
    val wordKey: String = TextNorm.fold(listOfNotNull(word, translit).joinToString(" "))
    val spanishKey: String = TextNorm.fold(spanish)
    val exampleKey: String = TextNorm.fold(listOfNotNull(example, exampleTranslit).joinToString(" "))

    /** Text to send to text-to-speech: the native script, never the transliteration. */
    val speakable: String get() = word

    /** Stable id for learning progress (survives data re-ordering). */
    val cardKey: String get() = "$word|$spanish"

    /** Reading aid shown under the word: transliteration, else pronunciation. */
    val readingAid: String? get() = translit ?: pron?.let { "[$it]" }
}

class LanguagePack(val lang: Lang, val entries: List<Entry>) {
    /** Sections in book order ("1. …", "2. …"), with the glossary last. */
    val sections: List<String> = entries.map { it.section }.distinct().sortedWith(
        compareBy<String> { sectionNumber(it) }.thenBy { it }
    )

    private fun sectionNumber(s: String): Int =
        if (s == GLOSSARY) Int.MAX_VALUE else s.substringBefore('.').trim().toIntOrNull() ?: (Int.MAX_VALUE - 1)

    companion object {
        const val GLOSSARY = "Glosario"
    }
}
