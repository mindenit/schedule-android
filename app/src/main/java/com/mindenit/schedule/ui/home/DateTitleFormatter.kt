package com.mindenit.schedule.ui.home

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateTitleFormatter {
    private val localeUk = Locale.forLanguageTag("uk")

    private fun ukTitleCase(s: String): String = s.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(localeUk) else ch.toString()
    }

    fun formatMonthTitle(ym: YearMonth): String {
        val monthName = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, localeUk)
        return "${ukTitleCase(monthName)} ${ym.year}"
    }

    fun formatDayTitle(date: LocalDate): String {
        val dow = ukTitleCase(date.dayOfWeek.getDisplayName(TextStyle.FULL, localeUk))
        val datePart = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", localeUk))
        return "$dow, $datePart"
    }
}

