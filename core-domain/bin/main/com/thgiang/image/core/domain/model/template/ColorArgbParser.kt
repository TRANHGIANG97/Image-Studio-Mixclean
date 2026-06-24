package com.thgiang.image.core.domain.model.template

/**
 * Parses CSS-like color strings to Android ARGB [Int] without android.graphics dependency.
 */
object ColorArgbParser {
    fun parseOrNull(color: String?): Int? {
        val raw = color?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: return null

        if (raw.startsWith("#")) {
            val hex = raw.removePrefix("#")
            return when (hex.length) {
                6 -> (0xFF000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                3 -> {
                    val expanded = buildString {
                        hex.forEach { ch -> append(ch).append(ch) }
                    }
                    (0xFF000000L or expanded.toLong(16)).toInt()
                }
                else -> null
            }
        }

        val rgba = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)""")
            .matchEntire(raw)
        if (rgba != null) {
            val r = rgba.groupValues[1].toInt().coerceIn(0, 255)
            val g = rgba.groupValues[2].toInt().coerceIn(0, 255)
            val b = rgba.groupValues[3].toInt().coerceIn(0, 255)
            val a = rgba.groupValues.getOrNull(4)
                ?.takeIf { it.isNotBlank() }
                ?.toFloatOrNull()
                ?.let { (it * 255f).toInt().coerceIn(0, 255) }
                ?: 255
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        return null
    }
}
