package net.myryzz.wacast.core

import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Template
import net.myryzz.wacast.model.TemplateVar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VarsTest {

    private val template = Template(id = "tpl_1", globals = mutableMapOf("invoice" to "GLOBAL-INV"))
    private val contact = Contact(
        name = "FENDY",
        values = mutableMapOf("tpl_1" to mutableMapOf("total" to "55.161")),
    )

    @Test
    fun detectsVariablesInOrderWithoutDuplicates() {
        assertEquals(
            listOf("nama", "total", "invoice"),
            detectVariables("Hello {{nama}}, total {{ total }} and {{nama}} / {{invoice}}"),
        )
        assertEquals(emptyList<String>(), detectVariables("no vars here"))
    }

    @Test
    fun resolvesContactThenNameThenGlobalsThenEmpty() {
        assertEquals("55.161", resolve("total", contact, template))
        assertEquals("FENDY", resolve("nama", contact, template))
        assertEquals("GLOBAL-INV", resolve("invoice", contact, template))
        assertEquals("", resolve("missing", contact, template))
    }

    @Test
    fun rendersBodyWithFallbacks() {
        assertEquals(
            "Hi FENDY / GLOBAL-INV / 55.161 / ",
            render("Hi {{nama}} / {{invoice}} / {{total}} / {{missing}}", contact, template),
        )
    }

    @Test
    fun rendersWithoutContactUsingGlobalsOnly() {
        assertEquals("Hi ! GLOBAL-INV", render("Hi {{nama}}! {{invoice}}", null, template))
    }

    @Test
    fun templateVarKeysUnionsDeclaredAndBodyVars() {
        val tpl = Template(
            id = "t",
            body = "Hi {{nama}} {{total}}",
            vars = mutableListOf(TemplateVar("invoice", "No. Invoice")),
        )
        assertEquals(listOf("invoice", "nama", "total"), templateVarKeys(tpl))
    }

    @Test
    fun varLabelFallsBackToKey() {
        val tpl = Template(id = "t", vars = mutableListOf(TemplateVar("invoice", "No. Invoice")))
        assertEquals("No. Invoice", varLabel(tpl, "invoice"))
        assertEquals("other", varLabel(tpl, "other"))
    }

    @Test
    fun resolvesAndListsKeysSafelyWhenTemplateIsNull() {
        assertEquals("FENDY", resolve("nama", contact, null))
        assertEquals("", resolve("total", contact, null))
        assertEquals(emptyList<String>(), templateVarKeys(null))
    }

    @Test
    fun templateVarKeysIgnoresMalformedVarsAndKeepsBodyOrder() {
        val tpl = Template(
            id = "t",
            body = "Hi {{nama}}, {{b}} then {{a}} then {{b}}",
            vars = mutableListOf(TemplateVar("bad key!"), TemplateVar(""), TemplateVar("c", "C")),
        )
        assertEquals(listOf("c", "nama", "b", "a"), templateVarKeys(tpl))
    }

    @Test
    fun autoDateBeatsManualTemplateDefault() {
        val tpl = Template(id = "tpl_1", auto = mutableMapOf("bulan" to "monthName"), globals = mutableMapOf("bulan" to "GLOBAL"))
        assertEquals(formatAutoDate("monthName"), resolve("bulan", contact, tpl))
    }

    @Test
    fun perContactValueBeatsAutoDate() {
        val tpl = Template(id = "tpl_1", auto = mutableMapOf("total" to "monthName"), globals = mutableMapOf("total" to "GLOBAL"))
        assertEquals("55.161", resolve("total", contact, tpl))
    }

    @Test
    fun invalidAutoPresetFallsThroughToManualDefault() {
        val tpl = Template(id = "tpl_1", auto = mutableMapOf("bulan" to "nope"), globals = mutableMapOf("bulan" to "GLOBAL"))
        assertEquals("GLOBAL", resolve("bulan", contact, tpl))
    }

    @Test
    fun autoDateWorksWithNoContactAndNoGlobals() {
        val tpl = Template(id = "tpl_1", auto = mutableMapOf("tahun" to "year"), globals = mutableMapOf())
        assertEquals(formatAutoDate("year"), resolve("tahun", null, tpl))
    }

    @Test
    fun resolveAndRenderRespectExplicitLang() {
        val tpl = Template(id = "tpl_1", auto = mutableMapOf("hari" to "dayName"))
        assertEquals(formatAutoDate("dayName", lang = "id"), resolve("hari", contact, tpl, "id"))
        assertEquals(formatAutoDate("dayName", lang = "en"), resolve("hari", contact, tpl, "en"))
        assertEquals(
            "Hari " + formatAutoDate("dayName", lang = "en"),
            render("Hari {{hari}}", contact, tpl, "en"),
        )
    }

    @Test
    fun testUnresolvedVariables() {
        val unresolved = unresolvedVariables("Halo {{nama}}, tagihan {{total}} dan kode {{promo}}", contact, template)
        assertEquals(listOf("promo"), unresolved)
    }

    @Test
    fun varPatternEscapesClosingBraces() {
        // Android's ICU regex throws PatternSyntaxException on a bare '}' at class
        // init (OpenJDK does not), which crashed ComposeFragment on device. Keep both escaped.
        assertEquals("""\{\{\s*([\w.-]+)\s*\}\}""", VAR_RE.pattern)
        assertFalse(VAR_RE.pattern.contains("""\s*}}"""))
    }
}
