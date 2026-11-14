package net.myryzz.wacast.core

/**
 * WhatsApp text formatting helpers. Mirrors the web app's `waFormat.js`.
 * Marker set: `*bold*`, `_italic_`, `~strike~`, `` `mono` ``.
 */

data class WrapResult(val text: String, val start: Int, val end: Int)

/** Wrap the selection (or insert an empty marker pair) with a WhatsApp formatting marker. */
fun wrapMarker(text: String?, start: Int?, end: Int?, marker: String): WrapResult {
    val src = text ?: ""
    val s = (start ?: src.length).coerceIn(0, src.length)
    val e = (end ?: s).coerceIn(s, src.length)
    val selected = src.substring(s, e)

    if (selected.length >= marker.length * 2 && selected.startsWith(marker) && selected.endsWith(marker)) {
        val inner = selected.substring(marker.length, selected.length - marker.length)
        return WrapResult(src.substring(0, s) + inner + src.substring(e), s, s + inner.length)
    }

    val next = src.substring(0, s) + marker + selected + marker + src.substring(e)
    return if (selected.isEmpty()) {
        WrapResult(next, s + marker.length, s + marker.length)
    } else {
        WrapResult(next, s + marker.length, e + marker.length)
    }
}

enum class SegmentType { TEXT, LINK, BOLD, ITALIC, STRIKE, MONO }

data class Segment(val type: SegmentType, val value: String)

private val TOKEN_RE = Regex("""(https?://[^\s<]+)|(\*[^\n*]+\*)|(_[^\n_]+_)|(~[^\n~]+~)|(`[^\n`]+`)""")

/**
 * Parse a single line of WhatsApp text into renderable segments without HTML injection.
 * Mirrors `tokenizeWaLine`.
 */
fun tokenizeWaLine(line: String?): List<Segment> {
    val src = line ?: ""
    val out = ArrayList<Segment>()
    var cursor = 0
    for (match in TOKEN_RE.findAll(src)) {
        val idx = match.range.first
        if (idx > cursor) out.add(Segment(SegmentType.TEXT, src.substring(cursor, idx)))
        val g = match.groupValues
        when {
            g[1].isNotEmpty() -> out.add(Segment(SegmentType.LINK, g[1]))
            g[2].isNotEmpty() -> out.add(Segment(SegmentType.BOLD, g[2].substring(1, g[2].length - 1)))
            g[3].isNotEmpty() -> out.add(Segment(SegmentType.ITALIC, g[3].substring(1, g[3].length - 1)))
            g[4].isNotEmpty() -> out.add(Segment(SegmentType.STRIKE, g[4].substring(1, g[4].length - 1)))
            g[5].isNotEmpty() -> out.add(Segment(SegmentType.MONO, g[5].substring(1, g[5].length - 1)))
        }
        cursor = idx + match.value.length
    }
    if (cursor < src.length) out.add(Segment(SegmentType.TEXT, src.substring(cursor)))
    return out
}
