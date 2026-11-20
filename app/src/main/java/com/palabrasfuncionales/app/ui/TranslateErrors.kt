package com.palabrasfuncionales.app.ui

import android.content.Context
import com.palabrasfuncionales.app.R
import com.palabrasfuncionales.app.net.TranslateException

object TranslateErrors {
    fun message(ctx: Context, e: TranslateException): String = when (e.kind) {
        TranslateException.Kind.NO_KEY -> ctx.getString(R.string.err_no_key)
        TranslateException.Kind.BAD_KEY -> ctx.getString(R.string.err_bad_key)
        TranslateException.Kind.FORBIDDEN -> ctx.getString(R.string.err_forbidden, e.message.orEmpty())
        TranslateException.Kind.QUOTA -> ctx.getString(R.string.err_quota)
        TranslateException.Kind.NETWORK -> ctx.getString(R.string.err_network)
        TranslateException.Kind.OTHER -> ctx.getString(R.string.err_other, e.message.orEmpty())
    }
}
