package net.myryzz.wacast.core

import java.util.Random

private val rnd = Random()
private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

/** Random 8-char base36-ish suffix, shape-compatible with the web app's `uid(prefix)`. */
fun uid(prefix: String): String {
    val out = StringBuilder(prefix).append('_')
    repeat(8) { out.append(ID_CHARS[rnd.nextInt(ID_CHARS.length)]) }
    return out.toString()
}
