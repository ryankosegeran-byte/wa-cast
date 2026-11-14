package net.myryzz.wacast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojisTest {

    @Test
    fun testLookupEmoji() {
        val wave = lookupEmoji("👋")
        assertEquals("Waving hand", wave.name)
        assertTrue(wave.kw.contains("halo"))

        val unknown = lookupEmoji("🛸")
        assertEquals("🛸", unknown.char)
    }

    @Test
    fun testFilterEmojis() {
        val matches = filterEmojis("uang")
        assertTrue(matches.any { it.char == "💰" })
        assertTrue(matches.any { it.char == "💵" })

        val heartMatches = filterEmojis("heart")
        assertTrue(heartMatches.size >= 5)

        val emptyMatches = filterEmojis("")
        assertTrue(emptyMatches.isEmpty())
    }

    @Test
    fun testCategoriesCount() {
        assertEquals(7, EMOJI_CATEGORIES.size)
        val totalEmojis = EMOJI_CATEGORIES.sumOf { it.emojis.size }
        assertTrue(totalEmojis >= 200)
    }
}
