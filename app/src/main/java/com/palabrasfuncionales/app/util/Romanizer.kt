package com.palabrasfuncionales.app.util

/**
 * Offline reading aid: turns Arabic, Cyrillic and Japanese kana into Latin
 * letters. Arabic is written without short vowels, so its result is only
 * approximate (the online mode gives a fully vowelled reading). Kanji stay as
 * they are offline.
 */
object Romanizer {
    fun needsRomanization(code: String): Boolean = code in setOf("ar", "ru", "ja")

    fun romanize(text: String, code: String): String? = when (code) {
        "ar" -> arabic(text)
        "ru" -> cyrillic(text)
        "ja" -> kana(text)
        else -> null
    }?.takeIf { it.isNotBlank() && it != text }

    // ---------------- Russian ----------------
    private val CYR = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "'", 'э' to "e", 'ю' to "yu",
        'я' to "ya",
    )

    fun cyrillic(s: String): String = buildString {
        for (ch in s) {
            val low = ch.lowercaseChar()
            val r = CYR[low]
            when {
                r == null -> append(ch)
                ch.isUpperCase() && r.isNotEmpty() -> append(r.replaceFirstChar { it.uppercaseChar() })
                else -> append(r)
            }
        }
    }

    // ---------------- Arabic ----------------
    private val AR_CONS = mapOf(
        'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "j", 'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "dh",
        'ر' to "r", 'ز' to "z", 'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z",
        'ع' to "'", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n",
        'ه' to "h", 'ء' to "'", 'ئ' to "'", 'ؤ' to "'", 'ة' to "a",
    )
    private val AR_VOWEL_MARKS = mapOf('َ' to "a", 'ُ' to "u", 'ِ' to "i", 'ً' to "an", 'ٌ' to "un", 'ٍ' to "in")
    private val AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun arabic(s: String): String {
        val out = StringBuilder()
        val words = s.split(' ')
        for ((wi, word) in words.withIndex()) {
            if (wi > 0) out.append(' ')
            var w = word
            if (w.startsWith("ال") && w.length > 2) {
                out.append("al-"); w = w.substring(2)
            } else if ((w.startsWith("وال") || w.startsWith("بال") || w.startsWith("فال") || w.startsWith("كال")) && w.length > 3) {
                out.append(AR_CONS[w[0]] ?: if (w[0] == 'و') "wa" else "")
                if (w[0] != 'و') out.append("i")
                out.append("l-"); w = w.substring(3)
            }
            var prev: String? = null
            for ((i, ch) in w.withIndex()) {
                val isFirst = i == 0
                val next = w.getOrNull(i + 1)
                val nextIsLetter = next != null && (AR_CONS.containsKey(next) || next in "اويىأإآ")
                val piece: String = when {
                    ch == 'ّ' -> prev?.lastOrNull()?.toString() ?: ""
                    AR_VOWEL_MARKS.containsKey(ch) -> AR_VOWEL_MARKS.getValue(ch)
                    ch == 'ْ' || ch == 'ـ' -> ""
                    ch == 'ا' -> if (isFirst) "a" else if (w.getOrNull(i - 1) == 'ً') "" else "ā"
                    ch == 'أ' -> "a"
                    ch == 'إ' -> "i"
                    ch == 'آ' -> "ā"
                    ch == 'ى' -> "ā"
                    ch == 'و' -> if (isFirst) "w" else if (prevIsConsonant(w, i) && (nextIsLetter || next == null)) "ū" else "w"
                    ch == 'ي' -> if (isFirst) "y" else if (prevIsConsonant(w, i)) "ī" else "y"
                    AR_CONS.containsKey(ch) -> AR_CONS.getValue(ch)
                    ch in AR_DIGITS -> (ch - '٠').toString()
                    ch == '؟' -> "?"
                    ch == '،' -> ","
                    else -> ch.toString()
                }
                out.append(piece)
                if (piece.isNotEmpty()) prev = piece
            }
        }
        return out.toString()
    }

    private fun prevIsConsonant(w: String, i: Int): Boolean {
        var j = i - 1
        while (j >= 0 && (AR_VOWEL_MARKS.containsKey(w[j]) || w[j] == 'ّ' || w[j] == 'ْ')) j--
        return j >= 0 && AR_CONS.containsKey(w[j])
    }

    // ---------------- Japanese kana ----------------
    private val KANA: Map<String, String> by lazy {
        val base = """
            あa いi うu えe おo かka きki くku けke こko さsa しshi すsu せse そso たta ちchi つtsu てte とto
            なna にni ぬnu ねne のno はha ひhi ふfu へhe ほho まma みmi むmu めme もmo やya ゆyu よyo
            らra りri るru れre ろro わwa をo んn がga ぎgi ぐgu げge ごgo ざza じji ずzu ぜze ぞzo
            だda ぢji づzu でde どdo ばba びbi ぶbu べbe ぼbo ぱpa ぴpi ぷpu ぺpe ぽpo ゔvu
            きゃkya きゅkyu きょkyo しゃsha しゅshu しょsho ちゃcha ちゅchu ちょcho にゃnya にゅnyu にょnyo
            ひゃhya ひゅhyu ひょhyo みゃmya みゅmyu みょmyo りゃrya りゅryu りょryo ぎゃgya ぎゅgyu ぎょgyo
            じゃja じゅju じょjo びゃbya びゅbyu びょbyo ぴゃpya ぴゅpyu ぴょpyo
            ぁa ぃi ぅu ぇe ぉo
        """.trimIndent()
        val m = HashMap<String, String>()
        for (tok in base.split(Regex("\\s+")).filter { it.isNotBlank() }) {
            val kana = tok.takeWhile { it.code > 0x3000 }
            val roma = tok.substring(kana.length)
            m[kana] = roma
            // Katakana is hiragana shifted by 0x60.
            m[kana.map { (it.code + 0x60).toChar() }.joinToString("")] = roma
        }
        m["ティ"] = "ti"; m["ディ"] = "di"; m["ファ"] = "fa"; m["フィ"] = "fi"; m["フェ"] = "fe"; m["フォ"] = "fo"
        m["ウィ"] = "wi"; m["ウェ"] = "we"; m["シェ"] = "she"; m["ジェ"] = "je"; m["チェ"] = "che"
        m
    }

    fun kana(s: String): String {
        val out = StringBuilder()
        var i = 0
        var doubleNext = false
        while (i < s.length) {
            val ch = s[i]
            if (ch == 'っ' || ch == 'ッ') { doubleNext = true; i++; continue }
            if (ch == 'ー') { out.lastOrNull()?.let { if (it in "aeiou") out.append(it) }; i++; continue }
            val two = if (i + 1 < s.length) s.substring(i, i + 2) else null
            val roma = two?.let { KANA[it] }?.also { i += 2 } ?: KANA[ch.toString()]?.also { i += 1 }
            if (roma == null) {
                out.append(when (ch) { '。' -> ". "; '、' -> ", "; '？' -> "?"; '！' -> "!"; '　' -> " "; else -> ch.toString() })
                i++
                doubleNext = false
                continue
            }
            if (doubleNext && roma.isNotEmpty()) out.append(if (roma.startsWith("ch")) 't' else roma[0])
            doubleNext = false
            // は as the topic particle is read "wa" only in context; keep the plain reading.
            out.append(roma)
        }
        return out.toString().trim()
    }
}
