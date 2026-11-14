package net.myryzz.wacast.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** ISO-8601 UTC timestamp with millis, matching JavaScript `new Date().toISOString()`. */
fun nowIso(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

/** `YYYY-MM-DD` for export filenames, matching `toISOString().slice(0, 10)`. */
fun todayIso(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())
