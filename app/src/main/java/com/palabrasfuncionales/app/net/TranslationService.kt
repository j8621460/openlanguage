package com.palabrasfuncionales.app.net

import android.content.Context
import com.palabrasfuncionales.app.security.SecureStore

/**
 * One entry point for translation. Default: free on-device Google ML Kit.
 * If the user saved an optional Google Cloud key, the Cloud API is used instead.
 */
object TranslationService {
    suspend fun translate(
        context: Context,
        text: String,
        source: String?,
        target: String,
        allowDownload: Boolean = true,
        onDownloading: (List<String>) -> Unit = {},
    ): Translation {
        val key = SecureStore(context).getApiKey()
        return if (!key.isNullOrBlank()) {
            GoogleTranslator.translate(context, key, text, source, target)
        } else {
            OfflineTranslator.translate(context, text.take(GoogleTranslator.MAX_INPUT_CHARS), source, target, allowDownload, onDownloading)
        }
    }
}
