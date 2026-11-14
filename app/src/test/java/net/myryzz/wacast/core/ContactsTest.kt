package net.myryzz.wacast.core

import net.myryzz.wacast.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {

    @Test
    fun parseDelimitedHandlesQuotesEscapedQuotesAndNewlines() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("x,1", "y\"2")),
            parseDelimited("a,b\n\"x,1\",\"y\"\"2\"\n", ','),
        )
    }

    @Test
    fun parseCsvAutoDetectsSemicolon() {
        assertEquals(
            listOf(listOf("nomor", "nama"), listOf("62811111", "Budi")),
            parseCsv("nomor;nama\n62811111;Budi\n"),
        )
    }

    @Test
    fun parseVcfReadsTelAndFnIncludingFoldedLines() {
        val vcf = "BEGIN:VCARD\nVERSION:3.0\nN:Santoso;Budi;;;\nFN:Budi \n Santoso\nTEL;TYPE=CELL:+62 811-1111\nEND:VCARD\n"
        assertEquals(listOf(VcfEntry("+62 811-1111", "Budi Santoso")), parseVcf(vcf))
    }

    @Test
    fun guessMappingFindsAliasesAndVariableKeys() {
        val map = guessMapping(listOf("Nomor WA", "Nama", "Tag", "invoice"), listOf("invoice"))
        assertEquals(0, map.phone)
        assertEquals(1, map.name)
        assertEquals(2, map.label)
        assertEquals(3, map.vars["invoice"])
    }

    @Test
    fun mapRowsNormalisesPhoneSplitsLabelsAndFillsValues() {
        val rows = mapRows(
            listOf(listOf("0896-9812-9722", "Budi", "VIP;Pelanggan", "INV-1")),
            Mapping(phone = 0, name = 1, label = 2, vars = mutableMapOf("invoice" to 3)),
            listOf("invoice"),
        )
        assertEquals(1, rows.size)
        assertEquals("6289698129722", rows[0].phone)
        assertEquals(listOf("VIP", "Pelanggan"), rows[0].labels)
        assertEquals(mapOf("invoice" to "INV-1"), rows[0].values)
        assertTrue(rows[0].valid)
    }

    @Test
    fun mergeContactsAddsNewAndUnionsLabelsOnDuplicates() {
        val local = mutableListOf(
            Contact(
                id = "c1",
                phone = "62811111",
                name = "A",
                labels = mutableListOf("VIP"),
                values = mutableMapOf("t1" to mutableMapOf("x" to "L")),
            ),
        )
        val result = mergeContacts(
            local,
            listOf(
                Contact(phone = "62811111", name = "B", labels = mutableListOf("Pelanggan"), values = mutableMapOf("t1" to mutableMapOf("x" to "F", "y" to "10"))),
                Contact(phone = "62822222", name = "C"),
            ),
        )
        assertEquals(1, result.added)
        assertEquals(1, result.merged)
        assertEquals(2, local.size)
        assertEquals("A", local[0].name)
        assertEquals(listOf("VIP", "Pelanggan"), local[0].labels)
        assertEquals("L", local[0].values["t1"]!!["x"])
        assertEquals("10", local[0].values["t1"]!!["y"])
    }

    @Test
    fun mergeContactsTakesNewestSentAt() {
        val local = mutableListOf(Contact(phone = "62811111", sentAt = mutableMapOf("t1" to "2026-01-01T00:00:00Z")))
        mergeContacts(local, listOf(Contact(phone = "62811111", sentAt = mutableMapOf("t1" to "2026-02-01T00:00:00Z"))))
        assertEquals("2026-02-01T00:00:00Z", local[0].sentAt["t1"])
    }

    @Test
    fun uniqueLabelsIsSortedAndUnique() {
        assertEquals(
            listOf("a", "b", "c"),
            uniqueLabels(listOf(Contact(labels = mutableListOf("b", "a")), Contact(labels = mutableListOf("a", "c")))),
        )
    }

    @Test
    fun invalidRowIsMarkedNotValid() {
        val rows = mapRows(listOf(listOf("12345", "X")), Mapping(phone = 0, name = 1), emptyList())
        assertFalse(rows[0].valid)
    }

    @Test
    fun detectDelimiterPrefersTheMostFrequentSeparator() {
        assertEquals(',', detectDelimiter("a,b,c"))
        assertEquals(';', detectDelimiter("a;b;c"))
        assertEquals('\t', detectDelimiter("a\tb\tc"))
        // CSV detection deliberately ignores '|', which separates labels inside a cell.
        assertEquals(',', detectDelimiter("6281|Nama|X"))
        // Paste rows use a pipe-first candidate list.
        assertEquals('|', detectDelimiter("6281|Nama|X", listOf('|', '\t', ',')))
    }

    @Test
    fun parsePasteRowsFillsVariablesInBodyOrderSkippingNama() {
        val rows = parsePasteRows(
            "6289698129722|FENDY STEVANUS RUUS|INV-1|55.161\n6281234567890|SITI|INV-2|42.000",
            listOf("nama", "invoice", "total"),
        )
        assertEquals(2, rows.size)
        assertEquals("FENDY STEVANUS RUUS", rows[0].name)
        assertEquals(mapOf("invoice" to "INV-1", "total" to "55.161"), rows[0].values)
        assertTrue(rows[0].valid)
        assertEquals(mapOf("invoice" to "INV-2", "total" to "42.000"), rows[1].values)
    }

    @Test
    fun parsePasteRowsAcceptsTabCommaAndPhoneOnlyRows() {
        assertEquals(
            listOf("62811111111"),
            parsePasteRows("62811111111\tBudi\tINV-9", listOf("invoice")).map { it.phone },
        )
        assertEquals(
            mapOf("invoice" to "INV-9"),
            parsePasteRows("62811111111,Budi,INV-9", listOf("invoice")).single().values,
        )
        val phoneOnly = parsePasteRows("62811111111", listOf("invoice")).single()
        assertTrue(phoneOnly.valid)
        assertEquals("", phoneOnly.name)
        assertTrue(phoneOnly.values.isEmpty())
    }
}
