package net.myryzz.wacast.core

import net.myryzz.wacast.model.Group
import net.myryzz.wacast.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateGroupsTest {

    private fun groups(vararg spec: Triple<String, String, String?>) =
        spec.map { (id, name, parentId) -> Group(id, name, parentId) }.toMutableList()

    private val tree = groups(
        Triple("root", "Usaha Roti", null),
        Triple("mid", "Pemberitahuan", "root"),
        Triple("leaf", "Pengingat Pembayaran", "mid"),
        Triple("other", "Lain", null),
    )

    @Test
    fun pathIsRootToLeaf() {
        assertEquals("Usaha Roti \u203A Pemberitahuan \u203A Pengingat Pembayaran", templateGroupPath(tree, "leaf"))
        assertEquals("Lain", templateGroupPath(tree, "other"))
        assertEquals("", templateGroupPath(tree, "missing"))
        assertEquals("", templateGroupPath(tree, null))
    }

    @Test
    fun descendantIdsIncludeSelfAndSubtrees() {
        assertEquals(setOf("root", "mid", "leaf"), templateGroupDescendantIds(tree, "root").toSet())
        assertEquals(listOf("other"), templateGroupDescendantIds(tree, "other"))
        assertEquals(emptyList<String>(), templateGroupDescendantIds(tree, null))
    }

    @Test
    fun createTemplateGroupReusesNames() {
        val g = groups(Triple("g1", "A", null))
        assertEquals("g1", createTemplateGroup(g, "A")!!.id)
        assertNull(createTemplateGroup(g, ""))
        assertEquals("g1", createTemplateGroup(g, "B", "g1")!!.parentId)
    }

    @Test
    fun setParentRejectsDescendantAndSelf() {
        assertFalse(setTemplateGroupParent(tree, "root", "leaf"))
        assertFalse(setTemplateGroupParent(tree, "root", "root"))
        assertFalse(setTemplateGroupParent(tree, "root", "nope"))
        assertTrue(setTemplateGroupParent(tree, "other", "root"))
        assertEquals("root", tree.first { it.id == "other" }.parentId)
    }

    @Test
    fun renameRejectsTakenNames() {
        assertTrue(renameTemplateGroup(tree, "Lain", "Lain Baru"))
        assertEquals("Lain Baru", tree.first { it.id == "other" }.name)
        assertFalse(renameTemplateGroup(tree, "Lain Baru", "Usaha Roti"))
        assertFalse(renameTemplateGroup(tree, "TidakAda", "X"))
        assertFalse(renameTemplateGroup(tree, "Lain Baru", "  "))
    }

    @Test
    fun deleteMovesTemplatesToParentAndPromotesChildren() {
        val templates = mutableListOf(
            Template(id = "t1", groupId = "mid"),
            Template(id = "t2", groupId = "other"),
            Template(id = "t3", groupId = null),
        )
        assertEquals(1, deleteTemplateGroup(tree, templates, "mid"))
        assertNull(tree.firstOrNull { it.id == "mid" })
        assertEquals("root", tree.first { it.id == "leaf" }.parentId)
        assertEquals("root", templates[0].groupId)
        assertEquals("other", templates[1].groupId)
        assertEquals(null, templates[2].groupId)
        assertEquals(0, deleteTemplateGroup(tree, templates, "missing"))
    }

    @Test
    fun assignTemplateGroupOnlyCountsRealChanges() {
        val templates = mutableListOf(Template(id = "t1", groupId = null), Template(id = "t2", groupId = "other"))
        assertEquals(1, assignTemplateGroup(templates, listOf("t1", "t2"), "other"))
        assertEquals("other", templates[0].groupId)
        assertEquals(0, assignTemplateGroup(templates, listOf("t1", "t2"), "other"))
    }

    @Test
    fun countsArePerDirectGroup() {
        val templates = mutableListOf(Template(id = "t1", groupId = "mid"), Template(id = "t2", groupId = "other"))
        val counts = templateGroupCounts(tree, templates)
        assertEquals(1, counts["mid"])
        assertEquals(1, counts["other"])
        assertEquals(0, counts["leaf"])
    }

    @Test
    fun filterTemplatesByGroupSubtreeQueryAndNone() {
        val templates = mutableListOf(
            Template(id = "t1", name = "Pengingat", groupId = "leaf"),
            Template(id = "t2", name = "Promo", groupId = "other"),
            Template(id = "t3", name = "Draft", groupId = null),
        )
        assertEquals(listOf("t1"), filterTemplates(templates, tree, focusGroupId = "root").map { it.id })
        assertEquals(listOf("t3"), filterTemplates(templates, tree, focusGroupId = "__none__").map { it.id })
        assertEquals(listOf("t1", "t2", "t3"), filterTemplates(templates, tree).map { it.id })
        assertEquals(listOf("t2"), filterTemplates(templates, tree, query = "promo").map { it.id })
        // Query also matches the group path.
        assertEquals(listOf("t1"), filterTemplates(templates, tree, query = "usaha roti").map { it.id })
    }
}
