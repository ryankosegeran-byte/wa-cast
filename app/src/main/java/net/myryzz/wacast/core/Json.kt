package net.myryzz.wacast.core

import net.myryzz.wacast.model.AppState
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Group
import net.myryzz.wacast.model.Template
import net.myryzz.wacast.model.TemplateVar
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialisation of [AppState] in the same shape the web app writes to `wa-broadcast:v1`,
 * so backups are interchangeable in both directions.
 */
object Json {

    fun encode(state: AppState): String = toJsonObject(state).toString(2)

    fun decode(text: String): JSONObject = JSONObject(text)

    private fun str(value: Any?): String =
        if (value == null || value === JSONObject.NULL) "" else value.toString()

    private fun groupsArray(groups: List<Group>): JSONArray {
        val array = JSONArray()
        for (g in groups) {
            array.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("parentId", g.parentId ?: JSONObject.NULL),
            )
        }
        return array
    }

    private fun stringMap(map: Map<String, String>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, v)
        return obj
    }

    fun toJsonObject(state: AppState): JSONObject {
        val templates = JSONArray()
        for (t in state.templates) {
            val vars = JSONArray()
            for (v in t.vars) vars.put(JSONObject().put("key", v.key).put("label", v.label))
            templates.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("body", t.body)
                    .put("globals", stringMap(t.globals))
                    .put("auto", stringMap(t.auto))
                    .put("vars", vars)
                    .put("groupId", t.groupId ?: JSONObject.NULL)
                    .put("createdAt", t.createdAt)
                    .put("updatedAt", t.updatedAt),
            )
        }

        val contacts = JSONArray()
        for (c in state.contacts) {
            val values = JSONObject()
            for ((tid, map) in c.values) values.put(tid, stringMap(map))
            contacts.put(
                JSONObject()
                    .put("id", c.id)
                    .put("phone", c.phone)
                    .put("name", c.name)
                    .put("labels", JSONArray(c.labels))
                    .put("values", values)
                    .put("sentAt", stringMap(c.sentAt)),
            )
        }

        return JSONObject()
            .put("schemaVersion", state.schemaVersion)
            .put("updatedAt", state.updatedAt)
            .put("groups", groupsArray(state.groups))
            .put("templateGroups", groupsArray(state.templateGroups))
            .put("templates", templates)
            .put("contacts", contacts)
    }

    // ---- decoding helpers, tolerant of missing / null / wrong-typed fields ----

    fun groupsOf(array: JSONArray?): List<Group> {
        val out = mutableListOf<Group>()
        for (i in 0 until (array?.length() ?: 0)) {
            val o = array!!.optJSONObject(i) ?: continue
            out.add(
                Group(
                    id = str(o.opt("id")),
                    name = str(o.opt("name")),
                    parentId = o.opt("parentId")?.let { str(it) }?.takeIf { it.isNotEmpty() },
                ),
            )
        }
        return out
    }

    fun stringMapOf(obj: JSONObject?): MutableMap<String, String> {
        val out = mutableMapOf<String, String>()
        if (obj == null) return out
        for (key in obj.keys()) out[key] = str(obj.opt(key))
        return out
    }

    fun nestedStringMapOf(obj: JSONObject?): MutableMap<String, MutableMap<String, String>> {
        val out = mutableMapOf<String, MutableMap<String, String>>()
        if (obj == null) return out
        for (key in obj.keys()) {
            val inner = obj.optJSONObject(key) ?: continue
            out[key] = stringMapOf(inner)
        }
        return out
    }

    fun templatesOf(array: JSONArray?): List<Template> {
        val out = mutableListOf<Template>()
        for (i in 0 until (array?.length() ?: 0)) {
            val o = array!!.optJSONObject(i) ?: continue
            val vars = mutableListOf<TemplateVar>()
            val rawVars = o.optJSONArray("vars")
            for (j in 0 until (rawVars?.length() ?: 0)) {
                val v = rawVars!!.optJSONObject(j) ?: continue
                vars.add(TemplateVar(str(v.opt("key")), str(v.opt("label"))))
            }
            out.add(
                Template(
                    id = str(o.opt("id")),
                    name = str(o.opt("name")),
                    body = str(o.opt("body")),
                    vars = vars,
                    auto = stringMapOf(o.optJSONObject("auto")),
                    globals = stringMapOf(o.optJSONObject("globals")),
                    groupId = o.opt("groupId")?.let { str(it) }?.takeIf { it.isNotEmpty() },
                    createdAt = str(o.opt("createdAt")),
                    updatedAt = str(o.opt("updatedAt")),
                ),
            )
        }
        return out
    }

    fun contactsOf(array: JSONArray?): List<Contact> {
        val out = mutableListOf<Contact>()
        for (i in 0 until (array?.length() ?: 0)) {
            val o = array!!.optJSONObject(i) ?: continue
            val labels = mutableListOf<String>()
            val rawLabels = o.optJSONArray("labels")
            for (j in 0 until (rawLabels?.length() ?: 0)) labels.add(str(rawLabels!!.opt(j)))
            out.add(
                Contact(
                    id = str(o.opt("id")),
                    phone = str(o.opt("phone")),
                    name = str(o.opt("name")),
                    labels = labels,
                    values = nestedStringMapOf(o.optJSONObject("values")),
                    sentAt = stringMapOf(o.optJSONObject("sentAt")),
                ),
            )
        }
        return out
    }
}
