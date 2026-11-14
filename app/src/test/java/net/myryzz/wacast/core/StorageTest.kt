package net.myryzz.wacast.core

import net.myryzz.wacast.model.AppState
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Group
import net.myryzz.wacast.model.Template
import net.myryzz.wacast.model.TemplateVar
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTest {

    @Test
    fun migrateFillsIdsNormalisesPhonesAndDropsInvalidData() {
        val raw = JSONObject(
            """
            {"schemaVersion":1,"updatedAt":"2026-01-01T00:00:00.000Z",
             "templates":[{"name":"T","body":"Hi {{nama}}","globals":{"a":"1"},
               "auto":{"b":"year","bad":"nope","c":"monthName"},
               "vars":[{"key":"a"},{"key":"a","label":"dup"},{"key":"bad key!"}],
               "groupId":"missing"}],
             "contacts":[{"phone":"0896-9812-9722","name":"Budi","labels":["VIP","VIP"," Pelanggan "]},
                         {"phone":"123","name":"Bad"}]}
            """.trimIndent(),
        )
        val state = migrate(raw)

        assertEquals(SCHEMA_VERSION, state.schemaVersion)
        assertEquals("2026-01-01T00:00:00.000Z", state.updatedAt)

        val tpl = state.templates.single()
        assertEquals("T", tpl.name)
        assertTrue(tpl.id.startsWith("tpl_"))
        assertEquals(1, tpl.vars.size)
        assertEquals(TemplateVar("a", ""), tpl.vars[0])
        assertEquals(mapOf("b" to "year", "c" to "monthName"), tpl.auto)
        assertNull(tpl.groupId)

        val contact = state.contacts.single()
        assertEquals("6289698129722", contact.phone)
        assertEquals(listOf("VIP", "Pelanggan"), contact.labels)
        assertTrue(contact.id.startsWith("c_"))

        assertEquals(listOf("VIP", "Pelanggan"), state.groups.map { it.name })
    }

    @Test
    fun migrateOfEmptyDocumentProducesEmptyState() {
        val state = migrate(null)
        assertEquals(SCHEMA_VERSION, state.schemaVersion)
        assertTrue(state.templates.isEmpty())
        assertTrue(state.contacts.isEmpty())
        assertTrue(state.groups.isEmpty())
        assertTrue(state.updatedAt.isNotEmpty())
    }

    @Test
    fun validateReportsShapeNewerAndPhoneErrors() {
        assertFalse(validateState(null).ok)
        assertEquals("shape", validateState(JSONObject("{}")).error)
        assertEquals("newer", validateState(JSONObject("""{"schemaVersion":99,"templates":[],"contacts":[]}""")).error)
        assertEquals(
            "phone",
            validateState(JSONObject("""{"schemaVersion":5,"templates":[],"contacts":[{"phone":"123"}]}""")).error,
        )
    }

    @Test
    fun validateReturnsCountsOnSuccess() {
        val result = validateState(
            JSONObject(
                """
                {"schemaVersion":5,"templates":[{"id":"t1","name":"T"}],
                 "contacts":[{"phone":"62811111111","name":"A","labels":["VIP","Pelanggan"]}]}
                """.trimIndent(),
            ),
        )
        assertTrue(result.ok)
        assertEquals(1, result.counts!!.templates)
        assertEquals(1, result.counts!!.contacts)
        assertEquals(2, result.counts!!.labels)
        assertEquals(2, result.counts!!.groups)
    }

    @Test
    fun mergeKeepsLocalAndOnlyFillsGaps() {
        val local = AppState(
            schemaVersion = SCHEMA_VERSION,
            templates = mutableListOf(
                Template(id = "t1", name = "T", vars = mutableListOf(TemplateVar("a", "")), globals = mutableMapOf("a" to "L")),
            ),
            contacts = mutableListOf(
                Contact(id = "c1", phone = "62811111111", name = "A", labels = mutableListOf("VIP"), values = mutableMapOf("t1" to mutableMapOf("x" to "L"))),
            ),
        )
        val incoming = AppState(
            schemaVersion = SCHEMA_VERSION,
            groups = mutableListOf(Group("g1", "Pelanggan", null)),
            templates = mutableListOf(
                Template(id = "t1", name = "T", vars = mutableListOf(TemplateVar("a", "A"), TemplateVar("b", "B")), globals = mutableMapOf("a" to "F", "b" to "10")),
                Template(id = "t2", name = "Baru", body = "Hi"),
            ),
            contacts = mutableListOf(
                Contact(id = "x1", phone = "62811111111", name = "B", labels = mutableListOf("Pelanggan"), values = mutableMapOf("t1" to mutableMapOf("x" to "F", "y" to "10"))),
                Contact(id = "x2", phone = "62822222222", name = "C"),
            ),
        )

        mergeStates(local, incoming)

        assertEquals(2, local.templates.size)
        val t1 = local.templates.first { it.id == "t1" }
        assertEquals(listOf(TemplateVar("a", "A"), TemplateVar("b", "B")), t1.vars)
        assertEquals(mapOf("a" to "L", "b" to "10"), t1.globals)

        assertEquals(2, local.contacts.size)
        val c1 = local.contacts.first { it.phone == "62811111111" }
        assertEquals("A", c1.name)
        assertEquals(listOf("VIP", "Pelanggan"), c1.labels)
        assertEquals("L", c1.values["t1"]!!["x"])
        assertEquals("10", c1.values["t1"]!!["y"])

        assertTrue(local.groups.any { it.name == "Pelanggan" })
        assertTrue(local.updatedAt.isNotEmpty())
    }

    @Test
    fun mergeRemapsTemplateGroupIdsByGroupName() {
        val local = AppState(schemaVersion = SCHEMA_VERSION)
        val incoming = AppState(
            schemaVersion = SCHEMA_VERSION,
            templateGroups = mutableListOf(
                Group("incRoot", "Usaha Roti", null),
                Group("incLeaf", "Pemberitahuan", "incRoot"),
            ),
            templates = mutableListOf(Template(id = "t1", name = "T", groupId = "incLeaf")),
        )

        mergeStates(local, incoming)

        val root = local.templateGroups.first { it.name == "Usaha Roti" }
        val leaf = local.templateGroups.first { it.name == "Pemberitahuan" }
        assertEquals(root.id, leaf.parentId)
        assertEquals(leaf.id, local.templates.single().groupId)
    }

    @Test
    fun jsonRoundTripIsStable() {
        val state = AppState(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = "2026-01-01T00:00:00.000Z",
            groups = mutableListOf(Group("g1", "Usaha Roti", null), Group("g2", "Pengingat", "g1")),
            templateGroups = mutableListOf(Group("tg1", "Bisnis", null)),
            templates = mutableListOf(
                Template(
                    id = "t1",
                    name = "Pengingat",
                    body = "Halo {{nama}}",
                    vars = mutableListOf(TemplateVar("nama", "Nama")),
                    auto = mutableMapOf("bulan" to "monthName"),
                    globals = mutableMapOf("total" to "0"),
                    groupId = "tg1",
                    createdAt = "2026-01-01T00:00:00.000Z",
                    updatedAt = "2026-01-01T00:00:00.000Z",
                ),
            ),
            contacts = mutableListOf(
                Contact(
                    id = "c1",
                    phone = "6289698129722",
                    name = "FENDY",
                    labels = mutableListOf("Usaha Roti", "Pengingat"),
                    values = mutableMapOf("t1" to mutableMapOf("total" to "55.161")),
                    sentAt = mutableMapOf("t1" to "2026-01-02T00:00:00.000Z"),
                ),
            ),
        )

        val once = migrate(Json.decode(Json.encode(state)))
        val twice = migrate(Json.decode(Json.encode(once)))

        assertEquals(Json.encode(once), Json.encode(twice))
        assertEquals(state.templates.single(), once.templates.single())
        assertEquals(state.contacts.single(), once.contacts.single())
        assertEquals(state.groups, once.groups)
        assertEquals(state.templateGroups, once.templateGroups)
    }

    @Test
    fun testValidateStateCounts() {
        val json = JSONObject(
            """
            {
              "schemaVersion": 5,
              "templates": [{"id":"t1","name":"T1","body":"Hello"}],
              "contacts": [{"id":"c1","phone":"08123456789","name":"Budi","labels":["VIP"]}],
              "groups": [{"id":"g1","name":"VIP"}],
              "templateGroups": []
            }
            """.trimIndent()
        )
        val res = validateState(json)
        assertTrue(res.ok)
        val counts = res.counts!!
        assertEquals(1, counts.templates)
        assertEquals(1, counts.contacts)
        assertEquals(1, counts.labels)
        assertEquals(1, counts.groups)
        assertEquals(0, counts.templateGroups)
    }
}
