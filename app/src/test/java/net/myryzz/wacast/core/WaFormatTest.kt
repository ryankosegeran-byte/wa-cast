package net.myryzz.wacast.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WaFormatTest {

    @Test
    fun wrapMarkerWrapsASelection() {
        val r = wrapMarker("hello", 0, 5, "*")
        assertEquals("*hello*", r.text)
        assertEquals(1, r.start)
        assertEquals(6, r.end)
    }

    @Test
    fun wrapMarkerUnwrapsAlreadyWrappedSelection() {
        val r = wrapMarker("*hello*", 0, 7, "*")
        assertEquals("hello", r.text)
        assertEquals(0, r.start)
        assertEquals(5, r.end)
    }

    @Test
    fun wrapMarkerWithEmptySelectionInsertsAPair() {
        val r = wrapMarker("", 0, 0, "*")
        assertEquals("**", r.text)
        assertEquals(1, r.start)
        assertEquals(1, r.end)
    }

    @Test
    fun tokenizeSplitsTextLinksAndMarkers() {
        assertEquals(
            listOf(
                Segment(SegmentType.TEXT, "Hi "),
                Segment(SegmentType.BOLD, "bold"),
                Segment(SegmentType.TEXT, " and "),
                Segment(SegmentType.LINK, "https://x.com/a"),
                Segment(SegmentType.TEXT, " "),
                Segment(SegmentType.ITALIC, "it"),
            ),
            tokenizeWaLine("Hi *bold* and https://x.com/a _it_"),
        )
    }

    @Test
    fun tokenizeHandlesPlainTextAndMarkersThatNeverClose() {
        assertEquals(listOf(Segment(SegmentType.TEXT, "plain")), tokenizeWaLine("plain"))
        assertEquals(listOf(Segment(SegmentType.TEXT, "*unclosed")), tokenizeWaLine("*unclosed"))
        assertEquals(listOf(Segment(SegmentType.STRIKE, "x"), Segment(SegmentType.TEXT, " "), Segment(SegmentType.MONO, "y")), tokenizeWaLine("~x~ `y`"))
    }
}
