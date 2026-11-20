package com.palabrasfuncionales.app.net

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation as MlTranslation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Free, key-less translation with Google ML Kit. Runs fully on the phone;
 * each language pack (~30 MB) is downloaded once from Google, after that it
 * works offline. Nothing you type is sent anywhere.
 */
object OfflineTranslator {
    private val clients = HashMap<String, Translator>()
    private val models = RemoteModelManager.getInstance()

    fun isSupported(code: String): Boolean = TranslateLanguage.fromLanguageTag(code) != null

    suspend fun isDownloaded(code: String): Boolean {
        val lang = TranslateLanguage.fromLanguageTag(code) ?: return false
        if (lang == TranslateLanguage.ENGLISH) return true
        return models.isModelDownloaded(TranslateRemoteModel.Builder(lang).build()).await()
    }

    suspend fun download(context: Context, code: String) {
        val lang = TranslateLanguage.fromLanguageTag(code) ?: return
        try {
            models.download(TranslateRemoteModel.Builder(lang).build(), conditions(context)).await()
        } catch (e: Exception) {
            throw TranslateException(e.message ?: "download failed", TranslateException.Kind.DOWNLOAD)
        }
    }

    suspend fun delete(code: String) {
        val lang = TranslateLanguage.fromLanguageTag(code) ?: return
        if (lang == TranslateLanguage.ENGLISH) return
        models.deleteDownloadedModel(TranslateRemoteModel.Builder(lang).build()).await()
    }

    /** Language of [text] as a code ("de", "ru"…), or null if unsure. */
    suspend fun detect(text: String): String? {
        val client = LanguageIdentification.getClient()
        return try {
            client.identifyLanguage(text).await().takeIf { it != "und" }
        } catch (e: Exception) {
            null
        } finally {
            client.close()
        }
    }

    /**
     * @param allowDownload false = fail with NEEDS_DOWNLOAD instead of fetching a
     *        language pack (used for translate-as-you-type).
     * @param onDownloading called with the language codes about to be downloaded.
     */
    suspend fun translate(
        context: Context,
        text: String,
        source: String?,
        target: String,
        allowDownload: Boolean = true,
        onDownloading: (List<String>) -> Unit = {},
    ): Translation {
        val src = source ?: detect(text)
            ?: throw TranslateException("detect", TranslateException.Kind.DETECT)
        val detected = if (source == null) src else null
        if (src == target) return Translation(text, detected)
        val s = TranslateLanguage.fromLanguageTag(src)
            ?: throw TranslateException(src, TranslateException.Kind.UNSUPPORTED)
        val t = TranslateLanguage.fromLanguageTag(target)
            ?: throw TranslateException(target, TranslateException.Kind.UNSUPPORTED)

        val missing = listOf(src, target).filterNot { isDownloaded(it) }
        if (missing.isNotEmpty()) {
            if (!allowDownload) throw TranslateException("download", TranslateException.Kind.NEEDS_DOWNLOAD)
            onDownloading(missing)
        }
        val client = synchronized(clients) {
            clients.getOrPut("$s>$t") {
                MlTranslation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build()
                )
            }
        }
        try {
            client.downloadModelIfNeeded(conditions(context)).await()
        } catch (e: Exception) {
            throw TranslateException(e.message ?: "download failed", TranslateException.Kind.DOWNLOAD)
        }
        return try {
            Translation(client.translate(text).await(), detected)
        } catch (e: Exception) {
            throw TranslateException(e.message ?: "translation failed")
        }
    }

    private fun conditions(context: Context): DownloadConditions {
        val wifiOnly = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
            .getBoolean(KEY_WIFI_ONLY, false)
        return DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
    }

    const val KEY_WIFI_ONLY = "downloads_wifi_only"
}

/** Google Play Tasks → coroutines, without an extra library. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
