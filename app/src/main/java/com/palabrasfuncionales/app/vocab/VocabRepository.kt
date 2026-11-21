package com.palabrasfuncionales.app.vocab

import android.content.Context
import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.data.LanguagePack
import com.palabrasfuncionales.app.data.Languages
import com.palabrasfuncionales.app.net.OfflineTranslator
import com.palabrasfuncionales.app.net.OnlineTranslator
import com.palabrasfuncionales.app.net.TranslateException
import com.palabrasfuncionales.app.net.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One everyday word or phrase of the core list (Spanish + English). */
data class VocabItem(val id: Int, val category: String, val es: String, val en: String) {
    /** English without "the", shown to the user. */
    val enShown: String get() = en.removePrefix("the ")
}

data class VocabWord(val text: String, val translit: String?)

/**
 * Everyday vocabulary (~400 words in 20 themes). The Spanish/English list ships
 * with the app; each language's words are translated with Google the first
 * time a theme is opened (one request per theme) and saved on the phone, so
 * afterwards they work offline.
 */
object VocabRepository {
    private var items: List<VocabItem>? = null
    private val cache = HashMap<String, MutableMap<Int, VocabWord>>()
    private val mutex = Mutex()

    suspend fun items(context: Context): List<VocabItem> = items ?: withContext(Dispatchers.IO) {
        val arr = JSONArray(context.assets.open("vocab/core.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            VocabItem(i, o.getString("c"), o.getString("es"), o.getString("en"))
        }
    }.also { items = it }

    fun categories(items: List<VocabItem>): List<String> = items.map { it.category }.distinct()

    suspend fun cached(context: Context, code: String): Map<Int, VocabWord> = mutex.withLock {
        loadLocked(context, code).toMap()
    }

    /** Translates the words of [list] not yet saved for [code]. Returns all saved words. */
    suspend fun translate(
        context: Context,
        code: String,
        list: List<VocabItem>,
        onDownloading: (List<String>) -> Unit = {},
    ): Map<Int, VocabWord> {
        val have = cached(context, code)
        val missing = list.filter { it.id !in have }
        if (missing.isEmpty()) return have
        val fresh = HashMap<Int, VocabWord>()
        var online = TranslationService.onlineEnabled(context)
        // Batches keep each request small; lines map 1:1 to words.
        for (batch in missing.chunked(40)) {
            if (online) {
                try {
                    fresh += onlineBatch(code, batch)
                    continue
                } catch (e: TranslateException) {
                    if (!OfflineTranslator.isSupported(code)) throw e
                    online = false
                }
            }
            for (item in batch) {
                val t = OfflineTranslator.translate(context, mtSource(item, code), "en", code, true, onDownloading)
                fresh[item.id] = VocabWord(clean(t.text), null)
            }
        }
        return mutex.withLock {
            val map = loadLocked(context, code)
            map += fresh
            save(context, code, map)
            map.toMap()
        }
    }

    private suspend fun onlineBatch(code: String, batch: List<VocabItem>): Map<Int, VocabWord> {
        val joined = batch.joinToString("\n") { mtSource(it, code) }
        val t = OnlineTranslator.translate(joined, "en", code)
        val lines = t.text.split('\n')
        val translits: List<String> = t.translit?.split('\n')?.takeIf { it.size == batch.size } ?: emptyList()
        if (lines.size == batch.size) {
            return batch.mapIndexed { i, item ->
                val tr = translits.getOrNull(i)?.let { clean(it) }?.takeIf { it.isNotBlank() }
                item.id to VocabWord(clean(lines[i]), tr)
            }.toMap()
        }
        // Google merged lines: fall back to one request per word.
        return batch.associate { item ->
            val one = OnlineTranslator.translate(mtSource(item, code), "en", code)
            item.id to VocabWord(clean(one.text), one.translit?.let(::clean))
        }
    }

    /** English sent for translation; Norwegian gets "a" (its "the" is a suffix). */
    internal fun mtSource(item: VocabItem, code: String): String =
        if (code == "no" && item.en.startsWith("the ")) "a " + item.en.removePrefix("the ") else item.en

    private fun clean(s: String) = s.trim().trimEnd('.', '。')

    /** Saved words as a pack, so the normal lessons, quiz and review work on them. */
    suspend fun pack(context: Context, code: String): LanguagePack {
        val all = items(context)
        val saved = cached(context, code)
        val entries = all.filter { it.id in saved }.mapIndexed { i, item ->
            val w = saved.getValue(item.id)
            Entry(
                id = i,
                word = w.text,
                translit = w.translit,
                pron = null,
                spanish = "${item.es} (${item.enShown})",
                example = null,
                exampleTranslit = null,
                exampleSpanish = null,
                note = null,
                section = item.category,
            )
        }
        return LanguagePack(Languages.byCode(code) ?: error("unknown $code"), entries)
    }

    private fun file(context: Context, code: String) = File(context.filesDir, "vocab_$code.json")

    private suspend fun loadLocked(context: Context, code: String): MutableMap<Int, VocabWord> =
        cache[code] ?: withContext(Dispatchers.IO) {
            val f = file(context, code)
            val map = HashMap<Int, VocabWord>()
            if (f.exists()) {
                try {
                    val o = JSONObject(f.readText())
                    for (k in o.keys()) {
                        val a = o.getJSONArray(k)
                        map[k.toInt()] = VocabWord(a.getString(0), a.optString(1, "").ifBlank { null })
                    }
                } catch (e: Exception) {
                    f.delete()
                }
            }
            map
        }.also { cache[code] = it }

    private suspend fun save(context: Context, code: String, map: Map<Int, VocabWord>) = withContext(Dispatchers.IO) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.toString(), JSONArray().put(v.text).put(v.translit ?: "")) }
        val f = file(context, code)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    suspend fun clear(context: Context) = mutex.withLock {
        cache.clear()
        withContext(Dispatchers.IO) {
            context.filesDir.listFiles { f -> f.name.startsWith("vocab_") }?.forEach { it.delete() }
        }
    }
}
