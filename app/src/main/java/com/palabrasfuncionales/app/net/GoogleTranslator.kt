package com.palabrasfuncionales.app.net

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.palabrasfuncionales.app.security.AppSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class TranslateException(message: String, val kind: Kind = Kind.OTHER) : Exception(message) {
    enum class Kind { NO_KEY, BAD_KEY, FORBIDDEN, QUOTA, NETWORK, OTHER, UNSUPPORTED, DETECT, NEEDS_DOWNLOAD, DOWNLOAD }
}

data class Translation(
    val text: String,
    val detectedSource: String?,
    /** Latin-letter pronunciation of [text] (Arabic, Japanese, Russian…), when known. */
    val translit: String? = null,
    /** Latin-letter pronunciation of the input text, when known. */
    val sourceTranslit: String? = null,
    /** True when [translit] is the offline approximation (Arabic has no vowels written). */
    val translitApprox: Boolean = false,
)

/**
 * Google Cloud Translation API (v2, "Basic").
 * The key goes in the X-Goog-Api-Key header (never in the URL, so it does not
 * end up in proxy or server logs). X-Android-Package / X-Android-Cert let the
 * user lock the key to this app in the Google Cloud console.
 */
object GoogleTranslator {
    private const val ENDPOINT = "https://translation.googleapis.com/language/translate/v2"
    private const val MAX_RESPONSE_BYTES = 512 * 1024
    const val MAX_INPUT_CHARS = 5000

    private val cache = LruCache<String, Translation>(300)

    suspend fun translate(
        context: Context,
        apiKey: String?,
        text: String,
        source: String?,
        target: String,
    ): Translation {
        if (apiKey.isNullOrBlank()) throw TranslateException("No API key", TranslateException.Kind.NO_KEY)
        val q = text.trim().take(MAX_INPUT_CHARS)
        val cacheKey = "${source ?: "auto"}|$target|$q"
        cache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) { request(context, apiKey, q, source, target) }
            .also { cache.put(cacheKey, it) }
    }

    private fun request(context: Context, apiKey: String, q: String, source: String?, target: String): Translation {
        val body = buildString {
            append("q=").append(enc(q))
            append("&target=").append(enc(target))
            append("&format=text")
            if (!source.isNullOrBlank()) append("&source=").append(enc(source))
        }.toByteArray(Charsets.UTF_8)

        val conn = URL(ENDPOINT).openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Goog-Api-Key", apiKey)
            conn.setRequestProperty("X-Android-Package", context.packageName)
            AppSignature.sha1(context)?.let { conn.setRequestProperty("X-Android-Cert", it) }
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_RESPONSE_BYTES) throw TranslateException("Response too large")
                String(bytes, Charsets.UTF_8)
            }.orEmpty()

            if (code !in 200..299) throw errorFor(code, response)
            val first = JSONObject(response)
                .getJSONObject("data")
                .getJSONArray("translations")
                .getJSONObject(0)
            return Translation(
                text = first.getString("translatedText"),
                detectedSource = first.optString("detectedSourceLanguage", "").ifEmpty { null },
            )
        } catch (e: TranslateException) {
            throw e
        } catch (e: IOException) {
            throw TranslateException("Network error: ${e.javaClass.simpleName}", TranslateException.Kind.NETWORK)
        } catch (e: JSONException) {
            throw TranslateException("Unexpected response from Google")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorFor(code: Int, body: String): TranslateException {
        val msg = try {
            JSONObject(body).getJSONObject("error").optString("message")
        } catch (e: JSONException) {
            ""
        }
        val kind = when {
            code == 400 && msg.contains("API key", ignoreCase = true) -> TranslateException.Kind.BAD_KEY
            code == 403 -> TranslateException.Kind.FORBIDDEN
            code == 429 -> TranslateException.Kind.QUOTA
            else -> TranslateException.Kind.OTHER
        }
        return TranslateException(msg.ifBlank { "HTTP $code" }, kind)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Fallback that needs no key: the Google Translate website / app. */
    fun webUri(text: String, source: String?, target: String): Uri =
        Uri.parse("https://translate.google.com/").buildUpon()
            .appendQueryParameter("sl", source ?: "auto")
            .appendQueryParameter("tl", target)
            .appendQueryParameter("text", text.take(MAX_INPUT_CHARS))
            .appendQueryParameter("op", "translate")
            .build()
}
