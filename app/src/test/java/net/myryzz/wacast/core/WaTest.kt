package net.myryzz.wacast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaTest {

    @Test
    fun normalizesLocalZeroEightNumbers() {
        assertEquals("6289698129722", Wa.normalizePhone("0896-9812-9722"))
        assertEquals("6289698129722", Wa.normalizePhone("08 96 9812 9722"))
    }

    @Test
    fun normalizesPlus62And62Numbers() {
        assertEquals("6289698129722", Wa.normalizePhone("+62 896-9812-9722"))
        assertEquals("6289698129722", Wa.normalizePhone("6289698129722"))
    }

    @Test
    fun rejectsNon62OrOutOfRangeNumbers() {
        assertTrue(Wa.isValidPhone(Wa.normalizePhone("089698129722")))
        assertFalse(Wa.isValidPhone(Wa.normalizePhone("12345")))
        assertFalse(Wa.isValidPhone(Wa.normalizePhone("1234567890123456789")))
        assertFalse(Wa.isValidPhone(Wa.normalizePhone("")))
    }

    @Test
    fun buildsWhatsAppLinkWithEncodedText() {
        assertEquals(
            "https://api.whatsapp.com/send?phone=6289698129722&text=%E2%8F%B0%20*Hi*%0Aline%202",
            Wa.buildWaUrl("6289698129722", "\u23F0 *Hi*\nline 2"),
        )
    }

    @Test
    fun encodeMatchesEncodeUriComponent() {
        assertEquals("a%20b", Wa.encodeURIComponent("a b"))
        assertEquals("-_.!~*'()", Wa.encodeURIComponent("-_.!~*'()"))
        assertEquals("%2B", Wa.encodeURIComponent("+"))
        assertEquals("%2F", Wa.encodeURIComponent("/"))
        assertEquals("%26%3D", Wa.encodeURIComponent("&="))
        assertEquals("%C3%A9", Wa.encodeURIComponent("\u00E9"))
    }
}
