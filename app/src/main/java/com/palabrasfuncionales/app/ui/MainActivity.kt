package com.palabrasfuncionales.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.palabrasfuncionales.app.R
import com.palabrasfuncionales.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            binding.bottomNav.isVisible = !imeVisible
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = if (imeVisible) ime.bottom else 0,
            )
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_learn
        }
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** Text selected in another app → "Palabras" → opens the translator with it. */
    private fun handleIncoming(intent: Intent?) {
        if (intent?.action != Intent.ACTION_PROCESS_TEXT) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        intent.action = null // handle once
        binding.bottomNav.selectedItemId = R.id.nav_translator
        supportFragmentManager.executePendingTransactions()
        (supportFragmentManager.findFragmentByTag(TAG_TRANSLATOR) as? TranslatorFragment)?.setInput(text)
            ?: TranslatorFragment.pendingInput.set(text)
    }

    private fun show(itemId: Int) {
        val tag = when (itemId) {
            R.id.nav_translator -> TAG_TRANSLATOR
            R.id.nav_settings -> TAG_SETTINGS
            R.id.nav_dictionary -> TAG_DICTIONARY
            else -> TAG_LEARN
        }
        val fm = supportFragmentManager
        fm.commit {
            setReorderingAllowed(true)
            for (t in listOf(TAG_LEARN, TAG_DICTIONARY, TAG_TRANSLATOR, TAG_SETTINGS)) {
                if (t != tag) fm.findFragmentByTag(t)?.let { hide(it) }
            }
            val existing = fm.findFragmentByTag(tag)
            if (existing != null) show(existing) else add(R.id.container, create(tag), tag)
        }
    }

    private fun create(tag: String): Fragment = when (tag) {
        TAG_TRANSLATOR -> TranslatorFragment()
        TAG_SETTINGS -> SettingsFragment()
        TAG_DICTIONARY -> DictionaryFragment()
        else -> LearnFragment()
    }

    companion object {
        const val TAG_LEARN = "learn"
        const val TAG_DICTIONARY = "dictionary"
        const val TAG_TRANSLATOR = "translator"
        const val TAG_SETTINGS = "settings"
    }
}
