package com.palabrasfuncionales.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Thin wrapper around the system text-to-speech engine. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: Pair<String, String>? = null
    private var onResult: ((Boolean) -> Unit)? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        val p = pending
        pending = null
        if (p != null) {
            val ok = ready && doSpeak(p.first, p.second)
            onResult?.invoke(ok)
        }
    }

    /** [callback] receives false when no voice is installed for that language. */
    fun speak(text: String, languageTag: String, callback: (Boolean) -> Unit) {
        onResult = callback
        if (!ready) {
            pending = text to languageTag
            return
        }
        callback(doSpeak(text, languageTag))
    }

    private fun doSpeak(text: String, languageTag: String): Boolean {
        val engine = tts ?: return false
        val result = engine.setLanguage(Locale.forLanguageTag(languageTag))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "palabras")
        return true
    }

    fun shutdown() {
        onResult = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
