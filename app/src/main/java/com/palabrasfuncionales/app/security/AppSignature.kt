package com.palabrasfuncionales.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * SHA-1 of the certificate that signed this APK. Sent as X-Android-Cert so the
 * Google Cloud API key can be restricted to this app (package + fingerprint).
 */
object AppSignature {
    @Volatile private var cached: String? = null

    fun sha1(context: Context): String? {
        cached?.let { return it }
        return try {
            val signatures = currentSignatures(context) ?: return null
            val first = signatures.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(first.toByteArray())
            digest.joinToString("") { "%02X".format(it) }.also { cached = it }
        } catch (e: Exception) {
            null
        }
    }

    /** Same value with colons, the way the Google Cloud console displays it. */
    fun sha1Pretty(context: Context): String? = sha1(context)?.chunked(2)?.joinToString(":")

    @Suppress("DEPRECATION")
    private fun currentSignatures(context: Context): Array<Signature>? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
    }
}
