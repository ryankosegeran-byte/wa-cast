package net.myryzz.wacast.core

import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsTest {

    private fun groups(vararg spec: Triple<String, String, String?>): MutableList<Group> =
        spec.map { (id, name, parentId) -> Group(id, name, parentId) }.toMutableList()

    @Test
    fun normalizeDedupsNamesResolvesParentByNameAndBreaksCycles() {
        val raw = listOf(
            Group("g1", " VIP ", null),
            Group("g2", "VIP", null),                    // duplicate name dropped
            Group("", "Jatuh Tempo", "Parent"),          // parent given by name
            Group("g4", "Parent", null),
            Group("g5", "A", "g6"),                      // cycle A -> B -> A
            Group("g6", "B", "g5"),
        )
        val rows = normalizeGroups(raw)
        assertEquals(listOf("VIP", "Jatuh Tempo", "Parent", "A", "B"), rows.map { it.name })
        assertEquals("g4", rows.first { it.name == "Jatuh Tempo" }.parentId)
        // The A -> B -> A cycle is broken by clearing the link that would close it.
        assertNull(rows.first { it.name == "B" }.parentId)
        assertEquals("g6", rows.first { it.name == "A" }.parentId)
    }

    @Test
    fun syncGroupsAddsMissingLabelsAsTopLevel() {
        val g = groups(Triple("g1", "VIP", null))
        syncGroups(g, listOf(Contact(phone = "1", labels = mutableListOf("VIP", "Baru"))))
        assertEquals(listOf("VIP", "Baru"), g.map { it.name })
        assertNull(g.first { it.name == "Baru" }.parentId)
    }

    @Test
    fun createGroupReturnsExistingOrNewOrNull() {
        val g = groups(Triple("g1", "VIP", null))
        assertEquals("g1", createGroup(g, "VIP")!!.id)
        assertNull(createGroup(g, "   "))
        val created = createGroup(g, "Pelanggan", "g1")!!
        assertEquals("g1", created.parentId)
        // Unknown parent falls back to top level.
        assertEquals(null, createGroup(g, "Lain", "nope")!!.parentId)
    }

    @Test
    fun ancestorsNearestFirstAndLabelsForGroupIncludeSelf() {
        val g = groups(
            Triple("root", "Usaha", null),
            Triple("mid", "Pemberitahuan", "root"),
            Triple("leaf", "Pengingat", "mid"),
        )
        assertEquals(listOf("Pemberitahuan", "Usaha"), groupAncestors(g, "leaf"))
        assertEquals(listOf("Pengingat", "Pemberitahuan", "Usaha"), labelsForGroup(g, "leaf"))
        assertEquals(emptyList<String>(), labelsForGroup(g, "missing"))
    }

    @Test
    fun expandLabelsAddsAncestorsAndDedups() {
        val g = groups(Triple("root", "Usaha", null), Triple("leaf", "Pengingat", "root"))
        assertEquals(listOf("Pengingat", "Usaha"), expandLabels(g, listOf("Pengingat", "Usaha", "")))
        assertEquals(listOf("Free"), expandLabels(g, listOf("Free")))
    }

    @Test
    fun backfillHierarchyIsAdditiveOnly() {
        val g = groups(Triple("root", "Usaha", null), Triple("leaf", "Pengingat", "root"))
        val c = Contact(phone = "1", labels = mutableListOf("Pengingat"))
        assertEquals(1, backfillHierarchy(g, listOf(c)))
        assertEquals(listOf("Pengingat", "Usaha"), c.labels)
        assertEquals(0, backfillHierarchy(g, listOf(c)))
    }

    @Test
    fun outlineIsDepthFirstWithDepth() {
        val g = groups(
            Triple("a", "Zeta", null),
            Triple("b", "Alpha", null),
            Triple("c", "Child", "b"),
        )
        assertEquals(
            listOf("Alpha" to 0, "Child" to 1, "Zeta" to 0),
            groupOutline(g).map { it.name to it.depth },
        )
    }

    @Test
    fun setGroupParentRejectsUnknownSelfAndDescendant() {
        val g = groups(Triple("root", "Usaha", null), Triple("leaf", "Pengingat", "root"))
        assertFalse(setGroupParent(g, "leaf", "nope"))
        assertFalse(setGroupParent(g, "root", "root"))
        assertFalse(setGroupParent(g, "root", "leaf"))
        assertTrue(setGroupParent(g, "leaf", null))
        assertNull(g.first { it.id == "leaf" }.parentId)
    }

    @Test
    fun renameLabelUpdatesGroupAndContacts() {
        val g = groups(Triple("g1", "VIP", null))
        val c = Contact(phone = "1", labels = mutableListOf("VIP", "Lain"))
        assertEquals(1, renameLabel(g, listOf(c), "VIP", "Prioritas"))
        assertEquals("Prioritas", g[0].name)
        assertEquals(listOf("Prioritas", "Lain"), c.labels)
        assertEquals(0, renameLabel(g, listOf(c), "TidakAda", "X"))
    }

    @Test
    fun deleteLabelStripsLabelAndPromotesChildren() {
        val g = groups(Triple("root", "Usaha", null), Triple("leaf", "Pengingat", "root"))
        val c = Contact(phone = "1", labels = mutableListOf("Usaha", "Pengingat"))
        assertEquals(1, deleteLabel(g, listOf(c), "Usaha"))
        assertEquals(listOf("Pengingat"), g.map { it.name })
        assertNull(g[0].parentId)
        assertEquals(listOf("Pengingat"), c.labels)
    }

    @Test
    fun assignLabelsAddAndRemove() {
        val c = Contact(id = "c1", phone = "1", labels = mutableListOf("A"))
        assertEquals(1, assignLabels(listOf(c), listOf("c1"), listOf("B", "A")))
        assertEquals(listOf("A", "B"), c.labels)
        assertEquals(1, assignLabels(listOf(c), listOf("c1"), listOf("A"), mode = "remove"))
        assertEquals(listOf("B"), c.labels)
        assertEquals(0, assignLabels(listOf(c), listOf("c1"), emptyList()))
    }

    @Test
    fun filterByLabelsAndOrModes() {
        val a = Contact(id = "a", phone = "1", labels = mutableListOf("X", "Y"))
        val b = Contact(id = "b", phone = "2", labels = mutableListOf("X"))
        assertEquals(listOf(a), filterByLabels(listOf(a, b), listOf("X", "Y")))
        assertEquals(listOf(a, b), filterByLabels(listOf(a, b), listOf("X", "Y"), mode = "or"))
        assertEquals(listOf(a, b), filterByLabels(listOf(a, b), emptyList()))
    }

    @Test
    fun splitBySentKeepsOrder() {
        val a = Contact(id = "a", phone = "1")
        val b = Contact(id = "b", phone = "2", sentAt = mutableMapOf("t1" to "2026-01-01T00:00:00Z"))
        val split = splitBySent(listOf(a, b), "t1")
        assertEquals(listOf("a"), split.unsent.map { it.id })
        assertEquals(listOf("b"), split.sent.map { it.id })
        assertEquals(listOf("a", "b"), splitBySent(listOf(a, b), null).unsent.map { it.id })
    }

    @Test
    fun labelHueIsDeterministicAndInRange() {
        assertEquals(labelHue("VIP"), labelHue("VIP"))
        assertTrue(labelHue("VIP") in 0..359)
        assertEquals(0, labelHue(""))
    }

    @Test
    fun buildLabelOptionsCountsAndPaths() {
        val g = groups(Triple("root", "Usaha", null), Triple("leaf", "Pengingat", "root"))
        val contacts = listOf(
            Contact(phone = "1", labels = mutableListOf("Pengingat")),
            Contact(phone = "2", labels = mutableListOf("Pengingat", "Usaha")),
        )
        val options = buildLabelOptions(listOf("Pengingat", "Usaha"), contacts, g)
        assertEquals(2, options[0].count)
        assertEquals("Usaha", options[0].parentPath)
        assertEquals(1, options[1].count)
        assertEquals("", options[1].parentPath)
    }

    @Test
    fun filterLabelOptionsRanksPrefixThenSubstringThenPath() {
        val options = listOf(
            LabelOption("Beta", 0, 0, ""),
            LabelOption("Alpha beta", 0, 0, ""),
            LabelOption("Gamma", 0, 0, "beta › Sub"),
        )
        assertEquals(listOf("Beta", "Alpha beta", "Gamma"), filterLabelOptions(options, "beta").map { it.name })
        assertEquals(options, filterLabelOptions(options, "  "))
    }

    @Test
    fun highlightTextSplitsMatches() {
        assertEquals(
            listOf(Highlight("Hell", false), Highlight("o", true), Highlight(" w", false), Highlight("o", true), Highlight("rld", false)),
            highlightText("Hello world", "o"),
        )
        assertEquals(listOf(Highlight("plain", false)), highlightText("plain", null))
        assertEquals(emptyList<Highlight>(), highlightText("", "x"))
    }
}
