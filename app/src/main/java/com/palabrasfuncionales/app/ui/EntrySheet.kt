package com.palabrasfuncionales.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.palabrasfuncionales.app.R
import com.palabrasfuncionales.app.data.DictionaryRepository
import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.data.Lang
import com.palabrasfuncionales.app.databinding.SheetEntryBinding
import com.palabrasfuncionales.app.net.GoogleTranslator
import com.palabrasfuncionales.app.net.TranslateException
import com.palabrasfuncionales.app.security.SecureStore
import com.palabrasfuncionales.app.util.Speaker
import com.palabrasfuncionales.app.util.copyToClipboard
import com.palabrasfuncionales.app.util.openUri
import com.palabrasfuncionales.app.util.toast
import kotlinx.coroutines.launch

/** Detail view of one dictionary entry, with English via Google Translate. */
class EntrySheet : BottomSheetDialogFragment() {
    private var _b: SheetEntryBinding? = null
    private val b get() = _b!!
    private var speaker: Speaker? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = SheetEntryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val code = requireArguments().getString(ARG_LANG) ?: return dismiss()
        val id = requireArguments().getInt(ARG_ID)
        speaker = Speaker(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val pack = DictionaryRepository.load(requireContext(), code)
            val entry = pack.entries.getOrNull(id) ?: return@launch dismiss()
            if (_b != null) bind(pack.lang, entry)
        }
    }

    private fun bind(lang: Lang, e: Entry) {
        b.language.text = getString(R.string.entry_language, lang.english, e.section)
        b.word.text = e.word
        b.translit.text = listOfNotNull(e.translit, e.pron?.let { "[$it]" }).joinToString("  ")
        b.translit.isVisible = b.translit.text.isNotBlank()
        b.meaning.text = e.spanish

        val hasExample = !e.example.isNullOrBlank() || !e.exampleTranslit.isNullOrBlank()
        b.exampleCard.isVisible = hasExample
        b.example.text = e.example ?: e.exampleTranslit
        b.exampleSpanish.text = e.exampleSpanish
        b.exampleSpanish.isVisible = !e.exampleSpanish.isNullOrBlank()

        b.noteCard.isVisible = !e.note.isNullOrBlank()
        b.note.text = e.note

        b.listen.setOnClickListener {
            speaker?.speak(e.speakable, lang.ttsTag) { ok ->
                if (!ok) context?.let { it.toast(it.getString(R.string.no_voice, lang.english)) }
            }
        }
        b.copy.setOnClickListener {
            requireContext().copyToClipboard(listOf(e.word, e.spanish).joinToString(" — "))
        }
        b.english.setOnClickListener { translate(lang, e) }
    }

    private fun translate(lang: Lang, e: Entry) {
        val ctx = requireContext()
        val key = SecureStore(ctx).getApiKey()
        if (key.isNullOrBlank()) {
            // No key: hand the meaning to Google Translate in the browser / app.
            ctx.toast(R.string.no_key_opening_web)
            ctx.openUri(GoogleTranslator.webUri(e.spanish, "es", "en"))
            return
        }
        b.english.isEnabled = false
        b.englishProgress.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // The Spanish meaning is more precise than the bare function word.
                val meaning = GoogleTranslator.translate(ctx, key, e.spanish, "es", "en")
                val example = e.example?.let { GoogleTranslator.translate(ctx, key, it, lang.code, "en") }
                    ?: e.exampleSpanish?.let { GoogleTranslator.translate(ctx, key, it, "es", "en") }
                if (_b == null) return@launch
                b.englishMeaning.text = meaning.text
                b.englishMeaning.isVisible = true
                b.englishLabel.isVisible = true
                b.exampleEnglish.text = example?.text
                b.exampleEnglish.isVisible = example != null
            } catch (ex: TranslateException) {
                ctx.toast(TranslateErrors.message(ctx, ex))
            } finally {
                _b?.let {
                    it.english.isEnabled = true
                    it.englishProgress.isVisible = false
                }
            }
        }
    }

    override fun onDestroyView() {
        speaker?.shutdown()
        speaker = null
        _b = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_LANG = "lang"
        private const val ARG_ID = "id"
        fun newInstance(code: String, id: Int) = EntrySheet().apply {
            arguments = bundleOf(ARG_LANG to code, ARG_ID to id)
        }
    }
}
