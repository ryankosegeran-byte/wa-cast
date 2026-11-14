package net.myryzz.wacast.core

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Auto date presets for template variables. Order is the order shown in the picker. */
val AUTO_PRESETS = listOf(
    "dateMonthYear",
    "monthName",
    "year",
    "monthNum",
    "monthYear",
    "dayName",
    "numericDate",
)

fun isAutoPreset(value: String?): Boolean = value != null && value in AUTO_PRESETS

private fun localeFor(lang: String?): Locale =
    if (lang == "id") Locale.forLanguageTag("id-ID") else Locale.US

private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/**
 * Value for an auto date variable. `preset` is one of [AUTO_PRESETS]; anything else returns "".
 * Month/day names and field order follow the app language. Numeric presets are always dd/MM/yyyy.
 */
fun formatAutoDate(preset: String?, date: Date = Date(), lang: String? = "id"): String {
    if (!isAutoPreset(preset)) return ""
    val locale = localeFor(lang)
    val cal = Calendar.getInstance().apply { time = date }
    fun fmt(pattern: String): String = SimpleDateFormat(pattern, locale).format(date)
    return when (preset) {
        // LONG date instance, not a literal pattern: en-US renders "March 15, 2026", id-ID "15 Maret 2026".
        "dateMonthYear" -> DateFormat.getDateInstance(DateFormat.LONG, locale).format(date)
        "monthName" -> fmt("MMMM")
        "year" -> fmt("yyyy")
        "monthNum" -> pad2(cal.get(Calendar.MONTH) + 1)
        "monthYear" -> pad2(cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.YEAR)
        "dayName" -> fmt("EEEE")
        "numericDate" -> pad2(cal.get(Calendar.DAY_OF_MONTH)) + "/" + pad2(cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.YEAR)
        else -> ""
    }
}
