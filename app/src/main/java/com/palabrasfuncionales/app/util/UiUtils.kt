package com.palabrasfuncionales.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.palabrasfuncionales.app.R

fun Context.copyToClipboard(text: String, sensitive: Boolean = false) {
    val cm = getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(getString(R.string.app_name), text)
    if (sensitive) {
        // Hides the value from the clipboard preview on Android 13+ (ignored before).
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    cm.setPrimaryClip(clip)
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(R.string.copied)
}

fun Context.toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

fun Context.openUri(uri: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        toast(R.string.no_browser)
    }
}
