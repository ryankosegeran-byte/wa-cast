package net.myryzz.wacast.core

import net.myryzz.wacast.model.AppState
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Group
import net.myryzz.wacast.model.Template
import net.myryzz.wacast.model.TemplateVar
import org.json.JSONObject

/**
 * Schema version, validation and state merging. Mirrors the web app's `storage.js`.
 * The storage backend itself (a file on Android instead of `localStorage`) lives in `StateStore`.
 */

const val SCHEMA_VERSION = 5
const val DATA_FILE = "wa-broadcast.json"
const val CORRUPT_FILE = "wa-broadcast.corrupt.json"

private val VAR_KEY_RE = Regex("""^[\w.-]+$""")

fun emptyState(): AppState = AppState(schemaVersion = SCHEMA_VERSION, updatedAt = nowIso())

/** Fill in fields added by later versions so old backups and old storage stay usable. */
fun migrate(raw: JSONObject?): AppState {
    val now = nowIso()
    val state = AppState(
        schemaVersion = SCHEMA_VERSION,
        updatedAt = raw?.let { if (it.has("updatedAt") && !it.isNull("updatedAt")) it.optString("updatedAt") else null } ?: now,
        groups = normalizeGroups(Json.groupsOf(raw?.optJSONArray("groups"))),
        templateGroups = normalizeTemplateGroups(Json.groupsOf(raw?.optJSONArray("templateGroups"))),
    )

    state.templates = Json.templatesOf(raw?.optJSONArray("templates")).map { t ->
        val auto = t.auto.filterKeys { key -> VAR_KEY_RE.matches(key) && isAutoPreset(t.auto[key]) }
        Template(
            id = t.id.ifEmpty { uid("tpl") },
            name = t.name,
            body = t.body,
            vars = t.vars
                .filter { VAR_KEY_RE.matches(it.key) }
                .distinctBy { it.key }
                .toMutableList(),
            auto = auto.toMutableMap(),
            globals = t.globals,
            groupId = t.groupId,
            createdAt = t.createdAt.ifEmpty { now },
            updatedAt = t.updatedAt.ifEmpty { now },
        )
    }.toMutableList()

    state.contacts = Json.contactsOf(raw?.optJSONArray("contacts")).mapNotNull { c ->
        val phone = Wa.normalizePhone(c.phone)
        Contact(
            id = c.id.ifEmpty { uid("c") },
            phone = phone,
            name = c.name,
            labels = c.labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList(),
            values = c.values,
            sentAt = c.sentAt,
        ).takeIf { Wa.isValidPhone(phone) }
    }.toMutableList()

    val templateGroupIds = state.templateGroups.mapTo(mutableSetOf()) { it.id }
    for (t in state.templates) {
        val gid = t.groupId
        if (!gid.isNullOrEmpty() && gid !in templateGroupIds) t.groupId = null
    }

    syncGroups(state.groups, state.contacts)
    return state
}

data class StateCounts(
    val templates: Int,
    val contacts: Int,
    val labels: Int,
    val groups: Int,
    val templateGroups: Int,
)

data class Validation(
    val ok: Boolean,
    val error: String = "",
    val data: AppState? = null,
    val counts: StateCounts? = null,
)

/** Validate a whole state document. Never touches local data on failure. */
fun validateState(raw: JSONObject?): Validation {
    if (raw == null) return Validation(false, "shape")
    val templates = raw.optJSONArray("templates")
    val contacts = raw.optJSONArray("contacts")
    if (templates == null || contacts == null) return Validation(false, "shape")
    if (raw.optInt("schemaVersion", 1) > SCHEMA_VERSION) return Validation(false, "newer")
    for (i in 0 until contacts.length()) {
        val c = contacts.optJSONObject(i)
        if (!Wa.isValidPhone(Wa.normalizePhone(c?.opt("phone")))) return Validation(false, "phone")
    }
    val data = migrate(raw)
    return Validation(
        ok = true,
        data = data,
        counts = StateCounts(
            templates = data.templates.size,
            contacts = data.contacts.size,
            labels = uniqueLabels(data.contacts).size,
            groups = data.groups.size,
            templateGroups = data.templateGroups.size,
        ),
    )
}

/**
 * Merge an incoming document into local. Local always wins; only empty fields and missing rows
 * are filled from `incoming`. Nothing is deleted.
 */
fun mergeStates(local: AppState, incoming: AppState): AppState {
    // ---- template groups: match by name, remap parents ----
    val tgByName = local.templateGroups.associateByTo(LinkedHashMap()) { it.name }
    val tgIds = local.templateGroups.mapTo(mutableSetOf()) { it.id }
    val tgRemap = HashMap<String, String>()
    val tgAdded = mutableListOf<Pair<Group, Group>>()
    for (g in incoming.templateGroups) {
        val name = g.name.trim()
        if (name.isEmpty()) continue
        val existing = tgByName[name]
        if (existing != null) {
            if (g.id.isNotEmpty()) tgRemap[g.id] = existing.id
            continue
        }
        var id = if (g.id.isNotEmpty() && g.id !in tgIds) g.id else uid("tgrp")
        while (id in tgIds) id = uid("tgrp")
        tgIds.add(id)
        val row = Group(id, name, null)
        tgByName[row.name] = row
        if (g.id.isNotEmpty()) tgRemap[g.id] = id
        tgAdded.add(g to row)
    }
    for ((src, row) in tgAdded) {
        val pid = src.parentId
        row.parentId = if (pid.isNullOrEmpty()) {
            null
        } else {
            (tgRemap[pid] ?: pid.takeIf { it in tgIds })?.takeIf { it != row.id }
        }
        local.templateGroups.add(row)
    }
    val tgById = local.templateGroups.associateBy { it.id }
    for ((_, row) in tgAdded) {
        val visited = mutableSetOf(row.id)
        var cur: Group? = row
        while (cur?.parentId != null) {
            val pid = cur.parentId!!
            if (pid in visited) {
                cur.parentId = null
                break
            }
            visited.add(pid)
            cur = tgById[pid]
        }
    }

    // ---- templates: match by id, then by name ----
    val byId = local.templates.associateByTo(LinkedHashMap()) { it.id }
    val byName = local.templates.associateByTo(LinkedHashMap()) { it.name.trim().lowercase() }
    for (t in incoming.templates) {
        val nameKey = t.name.trim().lowercase()
        if (byId.containsKey(t.id) || (nameKey.isNotEmpty() && byName.containsKey(nameKey))) continue
        val rawGroupId = t.groupId?.takeIf { it.isNotEmpty() }
        val next = t.copy(groupId = rawGroupId?.let { tgRemap[it] ?: it.takeIf { id -> id in tgIds } })
        local.templates.add(next)
        byId[next.id] = next
        if (nameKey.isNotEmpty()) byName[nameKey] = next
    }
    for (t in local.templates) {
        val source = incoming.templates.firstOrNull { it.id == t.id } ?: continue
        for (v in source.vars) {
            val cur = t.vars.firstOrNull { it.key == v.key }
            if (cur == null) t.vars.add(TemplateVar(v.key, v.label))
            else if (cur.label.isEmpty() && v.label.isNotEmpty()) cur.label = v.label
        }
        for ((key, value) in source.globals) if (t.globals[key].isNullOrEmpty()) t.globals[key] = value
        for ((key, preset) in source.auto) {
            if (!VAR_KEY_RE.matches(key) || !isAutoPreset(preset)) continue
            if (t.auto[key].isNullOrEmpty()) t.auto[key] = preset
        }
    }

    // ---- contact groups: match by name ----
    val gByName = local.groups.associateByTo(LinkedHashMap()) { it.name }
    val localIds = local.groups.mapTo(mutableSetOf()) { it.id }
    val incomingById = incoming.groups.associateBy { it.id }
    val addedGroups = mutableListOf<Group>()
    for (g in incoming.groups) {
        if (gByName.containsKey(g.name)) continue
        val id = if (g.id.isNotEmpty() && g.id !in localIds) g.id else uid("grp")
        localIds.add(id)
        val row = Group(id, g.name, g.parentId)
        local.groups.add(row)
        gByName[row.name] = row
        addedGroups.add(row)
    }
    val localById = local.groups.associateBy { it.id }
    for (row in addedGroups) {
        val pid = row.parentId
        if (pid.isNullOrEmpty()) continue
        val incomingParent = incomingById[pid]
        if (incomingParent != null) {
            val target = gByName[incomingParent.name]
            row.parentId = if (target != null && target.id != row.id) target.id else null
        } else if (pid !in localById || pid == row.id) {
            row.parentId = null
        }
    }

    mergeContacts(local.contacts, incoming.contacts)
    syncGroups(local.groups, local.contacts)
    local.updatedAt = nowIso()
    return local
}
