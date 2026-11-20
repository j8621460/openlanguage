package com.palabrasfuncionales.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.palabrasfuncionales.app.BuildConfig
import com.palabrasfuncionales.app.R
import com.palabrasfuncionales.app.databinding.FragmentSettingsBinding
import com.palabrasfuncionales.app.net.GoogleTranslator
import com.palabrasfuncionales.app.net.TranslateException
import com.palabrasfuncionales.app.security.AppSignature
import com.palabrasfuncionales.app.security.SecureStore
import com.palabrasfuncionales.app.util.copyToClipboard
import com.palabrasfuncionales.app.util.openUri
import com.palabrasfuncionales.app.util.toast
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private lateinit var store: SecureStore

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        store = SecureStore(requireContext())
        updateStatus()

        b.save.setOnClickListener {
            val key = b.apiKey.text?.toString()?.trim().orEmpty()
            if (!KEY_SHAPE.matches(key)) {
                b.apiKeyLayout.error = getString(R.string.key_looks_wrong)
                return@setOnClickListener
            }
            b.apiKeyLayout.error = null
            store.putApiKey(key)
            b.apiKey.setText("") // never keep the plain key on screen
            updateStatus()
            requireContext().toast(R.string.key_saved)
        }
        b.test.setOnClickListener { testKey() }
        b.remove.setOnClickListener {
            store.clear()
            updateStatus()
            requireContext().toast(R.string.key_removed)
        }
        b.getKey.setOnClickListener {
            requireContext().openUri(Uri.parse("https://console.cloud.google.com/apis/library/translate.googleapis.com"))
        }

        val pkg = requireContext().packageName
        val sha = AppSignature.sha1Pretty(requireContext()) ?: "?"
        b.packageName.text = pkg
        b.certSha.text = sha
        b.copyRestriction.setOnClickListener {
            requireContext().copyToClipboard("$pkg\n$sha")
        }
        b.version.text = getString(R.string.version_text, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    private fun updateStatus() {
        val has = store.hasApiKey()
        b.keyStatus.text = getString(if (has) R.string.key_status_saved else R.string.key_status_none)
        b.test.isVisible = has
        b.remove.isVisible = has
    }

    private fun testKey() {
        val ctx = requireContext()
        val key = store.getApiKey() ?: return updateStatus()
        b.test.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = GoogleTranslator.translate(ctx, key, "Hola, ¿qué tal?", "es", "en")
                ctx.toast(getString(R.string.key_works, r.text))
            } catch (e: TranslateException) {
                ctx.toast(TranslateErrors.message(ctx, e))
            } finally {
                _b?.test?.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    private companion object {
        // Google API keys: "AIza" + 35 URL-safe characters.
        val KEY_SHAPE = Regex("^AIza[0-9A-Za-z_\\-]{35}$")
    }
}
