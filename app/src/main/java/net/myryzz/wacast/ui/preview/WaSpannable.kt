package net.myryzz.wacast.ui.preview

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import net.myryzz.wacast.core.SegmentType
import net.myryzz.wacast.core.tokenizeWaLine

object WaSpannable {

    fun format(text: String, linkColor: Int = 0xFF0284C7.toInt()): CharSequence {
        if (text.isEmpty()) return ""
        val builder = SpannableStringBuilder()
        val lines = text.split('\n')

        for (i in lines.indices) {
            val line = lines[i]
            val segments = tokenizeWaLine(line)
            for (seg in segments) {
                val start = builder.length
                builder.append(seg.value)
                val end = builder.length

                val span: CharacterStyle? = when (seg.type) {
                    SegmentType.BOLD -> StyleSpan(Typeface.BOLD)
                    SegmentType.ITALIC -> StyleSpan(Typeface.ITALIC)
                    SegmentType.STRIKE -> StrikethroughSpan()
                    SegmentType.MONO -> TypefaceSpan("monospace")
                    SegmentType.LINK -> URLSpan(seg.value)
                    SegmentType.TEXT -> null
                }
                if (span != null) {
                    builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (seg.type == SegmentType.LINK) {
                    builder.setSpan(ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (i < lines.size - 1) {
                builder.append('\n')
            }
        }
        return builder
    }
}
