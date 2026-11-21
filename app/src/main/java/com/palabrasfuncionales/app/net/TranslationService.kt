package com.palabrasfuncionales.app.net

import android.content.Context
import com.palabrasfuncionales.app.security.SecureStore
import com.palabrasfuncionales.app.util.Romanizer

/**
 * One entry point for translation:
 * 1. optional Google Cloud key, if the user saved one;
 * 2. free online Google Translate (all languages + pronunciation), if enabled;
 * 3. free on-device Google ML Kit (works offline once a language is downloaded).
 */
object TranslationService {
    const val KEY_ONLINE = "online_translation"

    fun onlineEnabled(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(KEY_ONLINE, true)

    suspend fun translate(
        context: Context,
        text: String,
        source: String?,
        target: String,
        allowDownload: Boolean = true,
        onDownloading: (List<String>) -> Unit = {},
    ): Translation {
        val q = text.take(GoogleTranslator.MAX_INPUT_CHARS)
        val key = SecureStore(context).getApiKey()
        if (!key.isNullOrBlank()) return withReading(GoogleTranslator.translate(context, key, q, source, target), q, source, target)

        val offlinePossible = OfflineTranslator.isSupported(target) &&
            (source == null || OfflineTranslator.isSupported(source))
        if (onlineEnabled(context)) {
            try {
                return OnlineTranslator.translate(q, source, target)
            } catch (e: TranslateException) {
                if (!offlinePossible) throw e // e.g. Yoruba/Zulu with no connection
            }
        }
        return withReading(OfflineTranslator.translate(context, q, source, target, allowDownload, onDownloading), q, source, target)
    }

    /**
     * Offline there is no pronunciation from Google, so Arabic, Russian and
     * Japanese kana get an approximate one from [Romanizer] (marked «aprox.»).
     */
    private fun withReading(t: Translation, input: String, source: String?, target: String): Translation {
        var r = t
        if (r.translit.isNullOrBlank() && Romanizer.needsRomanization(target)) {
            Romanizer.romanize(r.text, target)?.let { r = r.copy(translit = it, translitApprox = true) }
        }
        val src = source ?: r.detectedSource
        if (r.sourceTranslit.isNullOrBlank() && src != null && Romanizer.needsRomanization(src)) {
            Romanizer.romanize(input, src)?.let { r = r.copy(sourceTranslit = it) }
        }
        return r
    }
}
