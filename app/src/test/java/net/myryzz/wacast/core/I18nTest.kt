package net.myryzz.wacast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nTest {

    @Test
    fun testKeyParityAndCount() {
        assertEquals(275, I18n.EN.size)
        assertEquals(275, I18n.ID.size)
        assertEquals(I18n.EN.keys, I18n.ID.keys)
    }

    @Test
    fun testInterpolation() {
        val res = I18n.t("importFound", mapOf("t" to 3, "c" to 10, "l" to 2), "en")
        assertEquals("File has 3 template(s), 10 contact(s), and 2 label(s). Variable definitions and labels are included.", res)

        val resId = I18n.t("importFound", mapOf("t" to 3, "c" to 10, "l" to 2), "id")
        assertEquals("File berisi 3 template, 10 kontak, dan 2 label. Definisi variabel dan label ikut tersimpan.", resId)
    }

    @Test
    fun testFallbackForMissingKey() {
        assertEquals("unknown_key_xyz", I18n.t("unknown_key_xyz", emptyMap(), "en"))
        assertEquals("unknown_key_xyz", I18n.t("unknown_key_xyz", emptyMap(), "id"))
    }

    @Test
    fun testListRetrieval() {
        val enFeatures = I18n.list("aboutFeatures", "en")
        val idFeatures = I18n.list("aboutFeatures", "id")
        assertTrue(enFeatures.isNotEmpty())
        assertTrue(idFeatures.isNotEmpty())
        assertEquals(enFeatures.size, idFeatures.size)

        val enHelp = I18n.list("helpItems", "en")
        val idHelp = I18n.list("helpItems", "id")
        assertTrue(enHelp.isNotEmpty())
        assertTrue(idHelp.isNotEmpty())
        assertEquals(enHelp.size, idHelp.size)
    }
}
