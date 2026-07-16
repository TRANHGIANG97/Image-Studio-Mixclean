package com.thgiang.image.studio.util

import android.content.Context

/** Persists recently used font family slugs for the label font picker. */
object FontRecents {
    private const val PREFS = "studio_font_recents"
    private const val KEY = "slugs"
    private const val MAX = 8

    fun list(context: Context): List<String> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun remember(context: Context, familySlug: String?) {
        val slug = familySlug?.takeIf { it.isNotBlank() } ?: return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = (listOf(slug) + list(context).filterNot { it.equals(slug, ignoreCase = true) })
            .take(MAX)
        prefs.edit().putString(KEY, next.joinToString(",")).apply()
    }
}
