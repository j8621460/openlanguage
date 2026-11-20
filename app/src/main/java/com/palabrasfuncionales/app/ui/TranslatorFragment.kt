package com.palabrasfuncionales.app.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.palabrasfuncionales.app.R
import com.palabrasfuncionales.app.data.Lang
import com.palabrasfuncionales.app.data.Languages
import com.palabrasfuncionales.app.databinding.FragmentTranslatorBinding
import com.palabrasfuncionales.app.net.GoogleTranslator
import com.palabrasfuncionales.app.net.TranslateException
import com.palabrasfuncionales.app.security.SecureStore
import com.palabrasfuncionales.app.util.Speaker
import com.palabrasfuncionales.app.util.copyToClipboard
import com.palabrasfuncionales.app.util.openUri
import com.palabrasfuncionales.app.util.toast
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class TranslatorFragment : Fragment() {
    private var _b: FragmentTranslatorBinding? = null
    private val b get() = _b!!
    private var speaker: Speaker? = null

    /** Source choices: null = detect automatically. */
    private val sources: List<Lang?> = listOf(null) + Languages.dictionary + Languages.spanish + Languages.english
    private val targets: List<Lang> = Languages.all
    private var source: Lang? = null
    private var target: Lang = Languages.english

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentTranslatorBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        speaker = Speaker(requireContext())
        val prefs = prefs()
        source = Languages.byCode(prefs.getString(KEY_SRC, null))
        target = Languages.byCode(prefs.getString(KEY_DST, "en")) ?: Languages.english

        val detect = getString(R.string.detect_language)
        b.source.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown, sources.map { it?.label ?: detect }))
        b.target.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown, targets.map { it.label }))
        b.source.setOnItemClickListener { _, _, pos, _ -> source = sources[pos]; saveLangs() }
        b.target.setOnItemClickListener { _, _, pos, _ -> target = targets[pos]; saveLangs() }
        showLangs()

        b.swap.setOnClickListener {
            val s = source ?: return@setOnClickListener requireContext().toast(R.string.swap_needs_source)
            val oldTarget = target
            target = s
            source = oldTarget
            val out = b.output.text.toString()
            if (out.isNotBlank()) b.input.setText(out)
            b.outputCard.isVisible = false
            showLangs()
            saveLangs()
        }

        b.inputLayout.counterMaxLength = GoogleTranslator.MAX_INPUT_CHARS
        b.translate.setOnClickListener { translate() }
        b.openWeb.setOnClickListener {
            val text = b.input.text?.toString().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            requireContext().openUri(GoogleTranslator.webUri(text, source?.code, target.code))
        }
        b.copyOutput.setOnClickListener {
            b.output.text.toString().takeIf { it.isNotBlank() }?.let { requireContext().copyToClipboard(it) }
        }
        b.listenOutput.setOnClickListener {
            val text = b.output.text.toString()
            if (text.isBlank()) return@setOnClickListener
            speaker?.speak(text, target.ttsTag) { ok ->
                if (!ok) context?.let { it.toast(it.getString(R.string.no_voice, target.english)) }
            }
        }
        pendingInput.getAndSet(null)?.let { setInput(it) }
    }

    override fun onResume() {
        super.onResume()
        _b?.noKeyHint?.isVisible = !SecureStore(requireContext()).hasApiKey()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) _b?.noKeyHint?.isVisible = !SecureStore(requireContext()).hasApiKey()
    }

    fun setInput(text: String) {
        val binding = _b
        if (binding == null) {
            pendingInput.set(text)
            return
        }
        binding.input.setText(text.take(GoogleTranslator.MAX_INPUT_CHARS))
        translate()
    }

    private fun showLangs() {
        b.source.setText(source?.label ?: getString(R.string.detect_language), false)
        b.target.setText(target.label, false)
    }

    private fun saveLangs() {
        prefs().edit().putString(KEY_SRC, source?.code).putString(KEY_DST, target.code).apply()
    }

    private fun translate() {
        val ctx = requireContext()
        val text = b.input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            b.inputLayout.error = getString(R.string.type_something)
            return
        }
        b.inputLayout.error = null
        val key = SecureStore(ctx).getApiKey()
        if (key.isNullOrBlank()) {
            ctx.toast(R.string.no_key_opening_web)
            ctx.openUri(GoogleTranslator.webUri(text, source?.code, target.code))
            return
        }
        b.translate.isEnabled = false
        b.progress.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = GoogleTranslator.translate(ctx, key, text, source?.code, target.code)
                if (_b == null) return@launch
                b.output.setText(result.text)
                b.outputCard.isVisible = true
                val detected = result.detectedSource?.let { Languages.byCode(it)?.english ?: it }
                b.detected.text = detected?.let { getString(R.string.detected_as, it) }
                b.detected.isVisible = source == null && detected != null
            } catch (e: TranslateException) {
                ctx.toast(TranslateErrors.message(ctx, e))
            } finally {
                _b?.let {
                    it.translate.isEnabled = true
                    it.progress.isVisible = false
                }
            }
        }
    }

    private fun prefs() = requireContext().getSharedPreferences("ui", Context.MODE_PRIVATE)

    override fun onDestroyView() {
        speaker?.shutdown()
        speaker = null
        _b = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_SRC = "translator_source"
        private const val KEY_DST = "translator_target"
        /** Text handed over from another app before this screen had a view. */
        val pendingInput = AtomicReference<String?>(null)
    }
}
