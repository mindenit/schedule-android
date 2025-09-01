package com.mindenit.schedule.ui.home

import android.view.View
import androidx.annotation.AttrRes
import com.google.android.material.color.MaterialColors
import kotlin.math.abs

object EventColorResolver {
    data class TypeColors(val foreground: Int, val background: Int)

    fun colorsForType(view: View, rawType: String): TypeColors {
        val type = rawType.lowercase().trim()
        // Preferred pairs of (background attr, foreground attr) using Material3 tokens
        val themePairs: List<Pair<Int, Int>> = listOf(
            com.google.android.material.R.attr.colorSecondaryContainer to com.google.android.material.R.attr.colorOnSecondaryContainer,
            com.google.android.material.R.attr.colorTertiaryContainer to com.google.android.material.R.attr.colorOnTertiaryContainer,
            com.google.android.material.R.attr.colorPrimaryContainer to com.google.android.material.R.attr.colorOnPrimaryContainer,
            com.google.android.material.R.attr.colorSurfaceVariant to com.google.android.material.R.attr.colorOnSurfaceVariant,
        )

        // If type is empty, prefer a stable default
        val index = if (type.isEmpty()) 0 else abs(type.hashCode()) % themePairs.size
        val (bgAttr, fgAttr) = themePairs[index]

        return try {
            val bg = getThemeColor(view, bgAttr)
            val fg = getThemeColor(view, fgAttr)
            TypeColors(foreground = fg, background = bg)
        } catch (_: IllegalArgumentException) {
            // Fallback to secondary container / on-secondary container or to surface/on-surface
            val bg = runCatching { getThemeColor(view, com.google.android.material.R.attr.colorSecondaryContainer) }
                .getOrElse { getThemeColor(view, com.google.android.material.R.attr.colorSurface) }
            val fg = runCatching { getThemeColor(view, com.google.android.material.R.attr.colorOnSecondaryContainer) }
                .getOrElse { getThemeColor(view, com.google.android.material.R.attr.colorOnSurface) }
            TypeColors(foreground = fg, background = bg)
        }
    }

    private fun getThemeColor(view: View, @AttrRes attr: Int): Int = MaterialColors.getColor(view, attr)
}

