package net.myryzz.wacast.core

/**
 * Phone normalisation + WhatsApp link building. Pure functions, mirror of the web app's `wa.js`.
 */
object Wa {

    /** Strip non-digits; rewrite a leading `0` to the Indonesian `62` country code. */
    fun normalizePhone(input: Any?): String {
        var digits = Regex("\\D").replace(input?.toString() ?: "", "")
        if (digits.startsWith("0")) digits = "62" + digits.substring(1)
        return digits
    }

    fun isValidPhone(phone: String): Boolean = Regex("^62\\d{6,13}$").matches(phone)

    fun buildWaUrl(phone: String, text: String): String =
        "https://api.whatsapp.com/send?phone=$phone&text=${encodeURIComponent(text)}"

    /** Characters `encodeURIComponent` leaves unescaped. */
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"

    /**
     * Exact port of JavaScript `encodeURIComponent`: UTF-8 percent-encoding, uppercase hex,
     * space as `%20` (not `+`), and `-_.!~*'()` kept literal. WhatsApp accepts this as-is.
     */
    fun encodeURIComponent(value: String): String {
        val out = StringBuilder(value.length)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val ch = code.toChar()
            if (code < 0x80 && UNRESERVED.indexOf(ch) >= 0) {
                out.append(ch)
            } else {
                out.append('%').append(String.format("%02X", code))
            }
        }
        return out.toString()
    }
}
