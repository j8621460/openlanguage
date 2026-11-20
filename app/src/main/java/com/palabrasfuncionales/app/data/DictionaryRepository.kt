package com.palabrasfuncionales.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Loads the bundled dictionaries (assets/dict/<code>.json) and searches them. Fully offline. */
object DictionaryRepository {
    private val cache = HashMap<String, LanguagePack>()
    private val mutex = Mutex()

    suspend fun load(context: Context, code: String): LanguagePack = mutex.withLock {
        cache[code] ?: withContext(Dispatchers.IO) {
            val json = context.applicationContext.assets.open("dict/$code.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(code, json)
        }.also { cache[code] = it }
    }

    internal fun parse(code: String, json: String): LanguagePack {
        val root = JSONObject(json)
        val lang = Languages.byCode(code) ?: error("Unknown language $code")
        val arr = root.getJSONArray("entries")
        val list = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val word = o.str("w") ?: continue
            val note = o.str("note")
            list += Entry(
                id = i,
                word = word,
                translit = o.str("tr"),
                pron = o.str("pron"),
                // A few table rows only describe usage; show that instead of an empty meaning.
                spanish = o.str("es") ?: note.orEmpty(),
                example = o.str("ex"),
                exampleTranslit = o.str("exTr"),
                exampleSpanish = o.str("exEs"),
                note = if (o.str("es") == null) null else note,
                section = o.str("sec") ?: LanguagePack.GLOSSARY,
            )
        }
        return LanguagePack(lang, list)
    }

    private fun JSONObject.str(name: String): String? =
        optString(name, "").trim().takeIf { it.isNotEmpty() }

    /** See [Search.run]. */
    fun search(pack: LanguagePack, query: String, section: String?): List<Entry> =
        Search.run(pack, query, section)
}
