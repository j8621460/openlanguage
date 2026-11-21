package com.palabrasfuncionales.app.learn

import android.content.Context

/** Saves learning progress per language on the device (SharedPreferences). */
class SrsStore(context: Context, code: String) {
    private val prefs = context.applicationContext.getSharedPreferences("srs_$code", Context.MODE_PRIVATE)

    fun all(): Map<String, CardState> = prefs.all.mapNotNull { (k, v) ->
        parse(v as? String)?.let { k to it }
    }.toMap()

    fun putAll(updates: Map<String, CardState>) {
        val ed = prefs.edit()
        updates.forEach { (k, s) -> ed.putString(k, "${s.box},${s.dueDay}") }
        ed.apply()
    }

    fun reset() = prefs.edit().clear().apply()

    private fun parse(v: String?): CardState? {
        val p = v?.split(',') ?: return null
        if (p.size != 2) return null
        val box = p[0].toIntOrNull() ?: return null
        val due = p[1].toLongOrNull() ?: return null
        return CardState(box, due)
    }
}

/** Days in a row with at least one finished session. */
object Streak {
    private const val PREFS = "stats"
    private const val K_LAST = "last_day"
    private const val K_COUNT = "streak"

    fun record(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = Srs.today()
        val last = p.getLong(K_LAST, Long.MIN_VALUE)
        val count = when (last) {
            today -> p.getInt(K_COUNT, 1)
            today - 1 -> p.getInt(K_COUNT, 0) + 1
            else -> 1
        }
        p.edit().putLong(K_LAST, today).putInt(K_COUNT, count).apply()
    }

    fun current(context: Context): Int {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = p.getLong(K_LAST, Long.MIN_VALUE)
        return if (last >= Srs.today() - 1) p.getInt(K_COUNT, 0) else 0
    }
}
