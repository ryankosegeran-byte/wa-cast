package net.myryzz.wacast.core

import net.myryzz.wacast.model.Group
import net.myryzz.wacast.model.Template

/** Template-group registry helpers. Mirrors the web app's `templateGroups.js`. */

private fun cleanName(s: String?): String = (s ?: "").trim()

/** Normalise raw registry rows: trimmed names, unique, valid parent, no cycles. */
fun normalizeTemplateGroups(raw: List<Group>?): MutableList<Group> {
    val rows = mutableListOf<Group>()
    val seenNames = mutableSetOf<String>()
    val seenIds = mutableSetOf<String>()
    for (g in raw ?: emptyList()) {
        val name = cleanName(g.name)
        if (name.isEmpty() || name in seenNames) continue
        seenNames.add(name)
        var id = if (g.id.isNotEmpty()) g.id else uid("tgrp")
        while (id in seenIds) id = uid("tgrp")
        seenIds.add(id)
        rows.add(Group(id, name, g.parentId?.takeIf { it.isNotEmpty() }))
    }
    val byId = rows.associateBy { it.id }
    val byName = rows.associateBy { it.name }
    for (g in rows) {
        val pid = g.parentId
        val parent = if (pid == null) null else byId[pid] ?: byName[cleanName(pid)]
        g.parentId = if (parent != null && parent.id != g.id) parent.id else null
    }
    for (g in rows) {
        val visited = mutableSetOf(g.id)
        var cur: Group? = g
        while (cur?.parentId != null) {
            val pid = cur.parentId!!
            if (pid in visited) {
                cur.parentId = null
                break
            }
            visited.add(pid)
            cur = byId[pid]
        }
    }
    return rows
}

/** Create a group if the name is free. Returns the existing or new row, or null for a blank name. */
fun createTemplateGroup(groups: MutableList<Group>, name: String?, parentId: String? = null): Group? {
    val name2 = cleanName(name)
    if (name2.isEmpty()) return null
    groups.firstOrNull { it.name == name2 }?.let { return it }
    val validParent = parentId?.takeIf { pid -> groups.any { it.id == pid } }
    val ids = groups.mapTo(mutableSetOf()) { it.id }
    var id = uid("tgrp")
    while (id in ids) id = uid("tgrp")
    val row = Group(id, name2, validParent)
    groups.add(row)
    return row
}

/** Flat display order: parents A-Z, each followed by its descendants, with a depth. */
fun templateGroupOutline(groups: List<Group>): List<OutlineNode> {
    val children = LinkedHashMap<String, MutableList<Group>>()
    for (g in groups) children.getOrPut(g.parentId ?: "") { mutableListOf() }.add(g)
    for (list in children.values) list.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val out = mutableListOf<OutlineNode>()
    fun walk(parentId: String, depth: Int) {
        for (g in children[parentId] ?: emptyList()) {
            out.add(OutlineNode(g.id, g.name, g.parentId, depth))
            walk(g.id, depth + 1)
        }
    }
    walk("", 0)
    return out
}

/** True when `maybeId` is `id` itself or sits below it. */
fun isTemplateGroupDescendant(groups: List<Group>, id: String, maybeId: String): Boolean {
    if (id == maybeId) return true
    val byId = groups.associateBy { it.id }
    val seen = mutableSetOf<String>()
    var cur = byId[maybeId]
    while (cur?.parentId != null && cur.parentId !in seen) {
        seen.add(cur.parentId!!)
        if (cur.parentId == id) return true
        cur = byId[cur.parentId!!]
    }
    return false
}

/** Reparent a group. Rejects unknown parents, self and descendants. Returns true on change. */
fun setTemplateGroupParent(groups: List<Group>, id: String, parentId: String?): Boolean {
    val g = groups.firstOrNull { it.id == id } ?: return false
    if (parentId != null && groups.none { it.id == parentId }) return false
    if (parentId != null && isTemplateGroupDescendant(groups, id, parentId)) return false
    g.parentId = parentId
    return true
}

/** Ancestor names of a group, nearest first, excluding the group itself. */
fun templateGroupAncestors(groups: List<Group>, id: String): List<String> {
    val byId = groups.associateBy { it.id }
    val out = mutableListOf<String>()
    val seen = mutableSetOf(id)
    var cur = byId[id]
    while (cur?.parentId != null && cur.parentId !in seen) {
        seen.add(cur.parentId!!)
        cur = byId[cur.parentId!!] ?: break
        out.add(cur.name)
    }
    return out
}

/** Breadcrumb path root to leaf, e.g. "Usaha Roti > Pemberitahuan". Empty for an unknown group. */
fun templateGroupPath(groups: List<Group>, id: String?): String {
    val byId = groups.associateBy { it.id }
    val g = id?.let { byId[it] } ?: return ""
    return (templateGroupAncestors(groups, g.id).reversed() + g.name).joinToString(" \u203A ")
}

/** Ids of a group and every group beneath it, including the group itself. Empty for a falsy id. */
fun templateGroupDescendantIds(groups: List<Group>, id: String?): List<String> {
    if (id.isNullOrEmpty()) return emptyList()
    return groups.filter { isTemplateGroupDescendant(groups, id, it.id) }.map { it.id }
}

/** Rename a group. Templates reference groups by id, so nothing else changes. */
fun renameTemplateGroup(groups: MutableList<Group>, from: String?, to: String?): Boolean {
    val oldName = cleanName(from)
    val newName = cleanName(to)
    if (oldName.isEmpty() || newName.isEmpty() || oldName == newName) return false
    val g = groups.firstOrNull { it.name == oldName } ?: return false
    if (groups.any { it.name == newName }) return false
    g.name = newName
    return true
}

/**
 * Delete a group: drop the row, promote subgroups one level, and move its templates to the deleted
 * group's parent (or "No group" when top level). Never deletes a template. Returns templates moved.
 */
fun deleteTemplateGroup(groups: MutableList<Group>, templates: List<Template>?, id: String): Int {
    val g = groups.firstOrNull { it.id == id } ?: return 0
    val parentId = g.parentId
    for (child in groups) if (child.parentId == g.id) child.parentId = parentId
    groups.remove(g)
    var moved = 0
    for (t in templates ?: emptyList()) {
        if (t.groupId != id) continue
        t.groupId = parentId
        moved++
    }
    return moved
}

/** Move the templates in `ids` into `groupId` (null = No group). Returns templates changed. */
fun assignTemplateGroup(templates: List<Template>?, ids: Collection<String>, groupId: String?): Int {
    val set = ids.toSet()
    val target = groupId?.takeIf { it.isNotEmpty() }
    var changed = 0
    for (t in templates ?: emptyList()) {
        if (t.id !in set) continue
        if (t.groupId == target) continue
        t.groupId = target
        changed++
    }
    return changed
}

/** Map of groupId to number of templates directly in that group (subgroups not counted). */
fun templateGroupCounts(groups: List<Group>, templates: List<Template>?): Map<String, Int> {
    val map = groups.associate { it.id to 0 }.toMutableMap()
    for (t in templates ?: emptyList()) {
        val gid = t.groupId
        if (gid != null && map.containsKey(gid)) map[gid] = map.getValue(gid) + 1
    }
    return map
}

/**
 * Templates matching the search query and the group filter.
 * `focusGroupId`: "" = all groups, "__none__" = only templates without a group, otherwise a group
 * id and all of its subgroups. `query` matches the template name or any name in its group path.
 */
fun filterTemplates(templates: List<Template>?, groups: List<Group>, query: String = "", focusGroupId: String = ""): List<Template> {
    val q = cleanName(query).lowercase()
    var list = templates ?: emptyList()
    if (focusGroupId == "__none__") {
        list = list.filter { it.groupId.isNullOrEmpty() }
    } else if (focusGroupId.isNotEmpty()) {
        val ids = templateGroupDescendantIds(groups, focusGroupId).toSet()
        list = list.filter { !it.groupId.isNullOrEmpty() && it.groupId in ids }
    }
    if (q.isEmpty()) return list
    return list.filter { t ->
        t.name.lowercase().contains(q) || templateGroupPath(groups, t.groupId).lowercase().contains(q)
    }
}
