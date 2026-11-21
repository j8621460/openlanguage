package com.palabrasfuncionales.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Free Google Translate without an API key: the same public endpoint the
 * Google Translate website and browser extensions use. It covers every
 * language here (Yoruba and Zulu too) and returns the pronunciation in Latin
 * letters (Arabic, Japanese, Russian…). It is unofficial, so it can be
 * rate-limited or change; the app then falls back to on-device ML Kit.
 */
object OnlineTranslator {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val MAX_RESPONSE_BYTES = 1024 * 1024

    suspend fun translate(text: String, source: String?, target: String): Translation =
        withContext(Dispatchers.IO) { request(text, source, target) }

    private fun request(text: String, source: String?, target: String): Translation {
        val url = URL(
            "$ENDPOINT?client=gtx&dt=t&dt=rm&ie=UTF-8&oe=UTF-8" +
                "&sl=${enc(source ?: "auto")}&tl=${enc(target)}"
        )
        val body = ("q=" + enc(text)).toByteArray(Charsets.UTF_8)
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Palabras")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code == 429) throw TranslateException("rate limited", TranslateException.Kind.QUOTA)
            if (code !in 200..299) throw TranslateException("HTTP $code")
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_RESPONSE_BYTES) throw TranslateException("Response too large")
            return parse(String(bytes, Charsets.UTF_8))
        } catch (e: TranslateException) {
            throw e
        } catch (e: IOException) {
            throw TranslateException("Network error", TranslateException.Kind.NETWORK)
        } catch (e: JSONException) {
            throw TranslateException("Unexpected response")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Response shape: [[["Hola","Hello",…], …, [null,null,"target translit","source translit"]], null, "en", …]
     */
    internal fun parse(json: String): Translation {
        val root = JSONArray(json)
        val segments = root.optJSONArray(0) ?: throw JSONException("no segments")
        val out = StringBuilder()
        var translit: String? = null
        var sourceTranslit: String? = null
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONArray(i) ?: continue
            if (!seg.isNull(0) && seg.opt(0) is String) {
                out.append(seg.getString(0))
            } else {
                if (seg.length() > 2 && !seg.isNull(2)) translit = seg.optString(2).ifBlank { null }
                if (seg.length() > 3 && !seg.isNull(3)) sourceTranslit = seg.optString(3).ifBlank { null }
            }
        }
        val detected = if (root.length() > 2 && !root.isNull(2)) root.optString(2).ifBlank { null } else null
        return Translation(out.toString(), detected, translit, sourceTranslit)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
