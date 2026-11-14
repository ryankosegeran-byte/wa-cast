package net.myryzz.wacast.core

import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Group

/**
 * Contact-group (label) registry helpers. Mirrors the web app's `groups.js`.
 *
 * A "group" is a label with an optional parent, so labels form a tree. Assigning a contact to a
 * subgroup also gives it the ancestor labels.
 */

private fun clean(s: String?): String = (s ?: "").trim()

/** Normalise raw registry rows: trimmed names, unique, valid parent, no cycles. */
fun normalizeGroups(raw: List<Group>?): MutableList<Group> {
    val rows = mutableListOf<Group>()
    val seenNames = mutableSetOf<String>()
    val seenIds = mutableSetOf<String>()
    for (g in raw ?: emptyList()) {
        val name = clean(g.name)
        if (name.isEmpty() || name in seenNames) continue
        seenNames.add(name)
        var id = if (g.id.isNotEmpty()) g.id else uid("grp")
        while (id in seenIds) id = uid("grp")
        seenIds.add(id)
        rows.add(Group(id, name, g.parentId?.takeIf { it.isNotEmpty() }))
    }
    val byId = rows.associateBy { it.id }
    val byName = rows.associateBy { it.name }
    for (g in rows) {
        val pid = g.parentId
        val parent = if (pid == null) null else byId[pid] ?: byName[clean(pid)]
        g.parentId = if (parent != null && parent.id != g.id) parent.id else null
    }
    // Break any remaining cycles.
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

/** Ensure every label used by a contact has a registry row (top level). Returns `groups`. */
fun syncGroups(groups: MutableList<Group>, contacts: List<Contact>?): MutableList<Group> {
    val knownNames = groups.mapTo(mutableSetOf()) { it.name }
    val ids = groups.mapTo(mutableSetOf()) { it.id }
    for (c in contacts ?: emptyList()) {
        for (label in c.labels) {
            val name = clean(label)
            if (name.isEmpty() || name in knownNames) continue
            knownNames.add(name)
            var id = uid("grp")
            while (id in ids) id = uid("grp")
            ids.add(id)
            groups.add(Group(id, name, null))
        }
    }
    return groups
}

/** Create a group if the name is free. Returns the existing or new row, or null for a blank name. */
fun createGroup(groups: MutableList<Group>, name: String?, parentId: String? = null): Group? {
    val cleanName = clean(name)
    if (cleanName.isEmpty()) return null
    groups.firstOrNull { it.name == cleanName }?.let { return it }
    val validParent = parentId?.takeIf { pid -> groups.any { it.id == pid } }
    var id = uid("grp")
    val ids = groups.mapTo(mutableSetOf()) { it.id }
    while (id in ids) id = uid("grp")
    val row = Group(id, cleanName, validParent)
    groups.add(row)
    return row
}

/** Ancestor names of a group, nearest first, excluding the group itself. */
fun groupAncestors(groups: List<Group>, id: String): List<String> {
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

/** Labels a contact should get when assigned to `id`: the group name plus its ancestors. */
fun labelsForGroup(groups: List<Group>, id: String): List<String> {
    val g = groups.firstOrNull { it.id == id } ?: return emptyList()
    return listOf(g.name) + groupAncestors(groups, id)
}

/** Expand a list of label names with the ancestor names of any that are registry groups. */
fun expandLabels(groups: List<Group>, names: List<String>?): List<String> {
    val out = LinkedHashSet<String>()
    for (raw in names ?: emptyList()) {
        val name = clean(raw)
        if (name.isEmpty()) continue
        out.add(name)
        val g = groups.firstOrNull { it.name == name }
        if (g != null) out.addAll(groupAncestors(groups, g.id))
    }
    return out.toList()
}

/**
 * Restore the parent-implies-child invariant: every contact in a group also carries that group's
 * ancestor names. Call after a hierarchy change. Additive only, so it cannot lose user data.
 *
 * ponytail: does not prune stale ancestor labels on un-nest. Add if un-nesting should prune.
 */
fun backfillHierarchy(groups: List<Group>, contacts: List<Contact>?): Int {
    val byName = groups.associateBy { it.name }
    var changed = 0
    for (c in contacts ?: emptyList()) {
        val own = c.labels
        val next = own.toMutableList()
        for (label in own) {
            val g = byName[label] ?: continue
            for (ancestor in groupAncestors(groups, g.id)) if (ancestor !in next) next.add(ancestor)
        }
        if (next.size != own.size) {
            c.labels = next
            changed++
        }
    }
    return changed
}

data class OutlineNode(val id: String, val name: String, val parentId: String?, val depth: Int)

/** Flat display order: parents A-Z, each followed by its descendants, with a depth. */
fun groupOutline(groups: List<Group>): List<OutlineNode> {
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
fun isDescendant(groups: List<Group>, id: String, maybeId: String): Boolean {
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
fun setGroupParent(groups: List<Group>, id: String, parentId: String?): Boolean {
    val g = groups.firstOrNull { it.id == id } ?: return false
    if (parentId != null && groups.none { it.id == parentId }) return false
    if (parentId != null && isDescendant(groups, id, parentId)) return false
    g.parentId = parentId
    return true
}

/** Rename a group and every contact label using the old name. Returns contacts changed. */
fun renameLabel(groups: MutableList<Group>, contacts: List<Contact>?, from: String?, to: String?): Int {
    val oldName = clean(from)
    val newName = clean(to)
    if (oldName.isEmpty() || newName.isEmpty() || oldName == newName) return 0
    if (groups.any { it.name == newName }) return 0
    groups.firstOrNull { it.name == oldName }?.name = newName
    var changed = 0
    for (c in contacts ?: emptyList()) {
        if (oldName !in c.labels) continue
        changed++
        c.labels = c.labels.map { if (it == oldName) newName else it }.distinct().toMutableList()
    }
    return changed
}

/** Delete a group: drop the row, strip the label, promote children one level. Returns contacts changed. */
fun deleteLabel(groups: MutableList<Group>, contacts: List<Contact>?, name: String?): Int {
    val target = clean(name)
    val g = groups.firstOrNull { it.name == target }
    if (g != null) {
        for (child in groups) if (child.parentId == g.id) child.parentId = g.parentId
        groups.remove(g)
    }
    var changed = 0
    for (c in contacts ?: emptyList()) {
        if (target !in c.labels) continue
        changed++
        c.labels = c.labels.filter { it != target }.toMutableList()
    }
    return changed
}

/** Add or remove labels on the contacts in `ids`. Returns contacts changed. */
fun assignLabels(contacts: List<Contact>?, ids: Collection<String>, labels: List<String>?, mode: String = "add"): Int {
    val set = ids.toSet()
    val names = (labels ?: emptyList()).map { clean(it) }.filter { it.isNotEmpty() }.distinct()
    if (names.isEmpty()) return 0
    var changed = 0
    for (c in contacts ?: emptyList()) {
        if (c.id !in set) continue
        val before = c.labels.joinToString("\u0000")
        c.labels = if (mode == "remove") {
            c.labels.filter { it !in names }.toMutableList()
        } else {
            (c.labels + names).distinct().toMutableList()
        }
        if (c.labels.joinToString("\u0000") != before) changed++
    }
    return changed
}

/** Contacts matching the label filter. `and` = every label, `or` = any label. */
fun filterByLabels(contacts: List<Contact>?, labels: List<String>?, mode: String = "and"): List<Contact> {
    val wanted = (labels ?: emptyList()).map { clean(it) }.filter { it.isNotEmpty() }.distinct()
    if (wanted.isEmpty()) return contacts ?: emptyList()
    return (contacts ?: emptyList()).filter { c ->
        if (mode == "or") wanted.any { it in c.labels } else wanted.all { it in c.labels }
    }
}

data class SentSplit(val unsent: List<Contact>, val sent: List<Contact>)

/** Split contacts into unsent / sent parts for a template id, preserving order. */
fun splitBySent(contacts: List<Contact>?, templateId: String?): SentSplit {
    val unsent = mutableListOf<Contact>()
    val sent = mutableListOf<Contact>()
    for (c in contacts ?: emptyList()) {
        if (!templateId.isNullOrEmpty() && !c.sentAt[templateId].isNullOrEmpty()) sent.add(c) else unsent.add(c)
    }
    return SentSplit(unsent, sent)
}

/** Deterministic 0..359 hue for a label name. */
fun labelHue(name: String?): Int {
    val s = name ?: ""
    var h = 0
    for (ch in s) h = (h * 31 + ch.code) % 360
    return h
}

data class LabelOption(val name: String, val count: Int, val hue: Int, val parentPath: String)

/** Build label filter items with contact counts, hue, and parent breadcrumb path. */
fun buildLabelOptions(labelPool: List<String>?, contacts: List<Contact>? = emptyList(), groups: List<Group>? = emptyList()): List<LabelOption> {
    val counts = HashMap<String, Int>()
    for (c in contacts ?: emptyList()) {
        for (raw in c.labels.distinct()) {
            val name = clean(raw)
            if (name.isNotEmpty()) counts[name] = (counts[name] ?: 0) + 1
        }
    }
    val byName = (groups ?: emptyList()).associateBy { it.name }
    return (labelPool ?: emptyList()).map { name ->
        val g = byName[name]
        val parentPath = if (g != null) groupAncestors(groups ?: emptyList(), g.id).reversed().joinToString(" \u203A ") else ""
        LabelOption(name, counts[name] ?: 0, labelHue(name), parentPath)
    }
}

/** Filter and rank label options by name (prefix first, then substring) or parent path. */
fun filterLabelOptions(options: List<LabelOption>?, query: String?): List<LabelOption> {
    val q = clean(query).lowercase()
    if (q.isEmpty()) return options ?: emptyList()
    val hits = mutableListOf<Pair<LabelOption, Int>>()
    for (opt in options ?: emptyList()) {
        val idx = opt.name.lowercase().indexOf(q)
        when {
            idx == 0 -> hits.add(opt to 0)
            idx > 0 -> hits.add(opt to 1)
            opt.parentPath.lowercase().contains(q) -> hits.add(opt to 2)
        }
    }
    return hits.sortedBy { it.second }.map { it.first }
}

data class Highlight(val text: String, val match: Boolean)

/** Split `text` into matched / unmatched segments for safe highlight rendering. */
fun highlightText(text: String?, query: String?): List<Highlight> {
    val src = text ?: ""
    if (src.isEmpty()) return emptyList()
    val q = clean(query).lowercase()
    if (q.isEmpty()) return listOf(Highlight(src, false))
    val lower = src.lowercase()
    val out = mutableListOf<Highlight>()
    var pos = 0
    while (pos < src.length) {
        val idx = lower.indexOf(q, pos)
        if (idx < 0) {
            out.add(Highlight(src.substring(pos), false))
            break
        }
        if (idx > pos) out.add(Highlight(src.substring(pos, idx), false))
        out.add(Highlight(src.substring(idx, idx + q.length), true))
        pos = idx + q.length
    }
    return out
}
