package net.myryzz.wacast.core

import net.myryzz.wacast.model.Contact

/**
 * CSV / VCF parsing, column mapping and contact merging. Mirrors the web app's `contacts.js`.
 */

/** Parse a delimited text block, RFC 4180-ish (quotes, escaped quotes, BOM tolerant). */
fun parseDelimited(text: String?, delimiter: Char = ','): List<List<String>> {
    val src = (text ?: "").removePrefix("\uFEFF").replace(Regex("\r\n?"), "\n")
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < src.length) {
        val ch = src[i]
        if (quoted) {
            if (ch == '"') {
                if (i + 1 < src.length && src[i + 1] == '"') {
                    field.append('"')
                    i++
                } else {
                    quoted = false
                }
            } else {
                field.append(ch)
            }
        } else when (ch) {
            '"' -> quoted = true
            delimiter -> { row.add(field.toString()); field.setLength(0) }
            '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = ArrayList() }
            else -> field.append(ch)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row)
    }
    return rows.filter { r -> r.any { it.trim().isNotEmpty() } }
}

/** Auto-detect the delimiter from the first line, choosing from [candidates] by frequency. */
fun detectDelimiter(text: String?, candidates: List<Char> = listOf(',', ';', '\t')): Char {
    val first = (text ?: "").removePrefix("\uFEFF").split(Regex("\r?\n")).firstOrNull() ?: ""
    val best = candidates
        .map { it to first.split(it).size - 1 }
        .maxByOrNull { it.second }!!
    return if (best.second > 0) best.first else candidates.first()
}

/** Parse a CSV block, auto-detecting the delimiter. `|` is excluded: it separates labels inside a cell. */
fun parseCsv(text: String?): List<List<String>> = parseDelimited(text, detectDelimiter(text))

data class VcfEntry(val phone: String, val name: String)

/** Parse a vCard file: one entry per vCard, first TEL + FN (fallback N). */
fun parseVcf(text: String?): List<VcfEntry> {
    val lines = (text ?: "").removePrefix("\uFEFF").replace(Regex("\r\n?"), "\n").split("\n")
    val unfolded = ArrayList<String>()
    for (line in lines) {
        if (line.isNotEmpty() && (line[0] == ' ' || line[0] == '\t') && unfolded.isNotEmpty()) {
            unfolded[unfolded.size - 1] += line.substring(1)
        } else {
            unfolded.add(line)
        }
    }
    val out = ArrayList<VcfEntry>()
    var phone = ""
    var fn = ""
    var n = ""
    var inCard = false
    for (raw in unfolded) {
        val line = raw.trim()
        if (line.equals("BEGIN:VCARD", ignoreCase = true)) {
            phone = ""; fn = ""; n = ""; inCard = true
            continue
        }
        if (line.equals("END:VCARD", ignoreCase = true)) {
            if (inCard && phone.isNotEmpty()) out.add(VcfEntry(phone, if (fn.isNotEmpty()) fn else n))
            inCard = false
            continue
        }
        if (!inCard) continue
        val idx = line.indexOf(':')
        if (idx < 0) continue
        val key = line.substring(0, idx).split(';')[0].substringAfterLast('.').uppercase()
        val value = line.substring(idx + 1).trim()
        if (value.isEmpty()) continue
        when {
            key == "TEL" && phone.isEmpty() -> phone = value
            key == "FN" && fn.isEmpty() -> fn = value.replace(';', ' ').trim()
            key == "N" && n.isEmpty() -> n = value.replace(';', ' ').replace(Regex("\\s+"), " ").trim()
        }
    }
    return out
}

private val HEADER_ALIASES = mapOf(
    "phone" to listOf("phone", "nomor", "tel", "telepon", "hp", "wa", "whatsapp", "nomorwa", "msisdn", "mobile"),
    "name" to listOf("name", "nama", "fn", "fullname", "kontak"),
    "label" to listOf("label", "tag", "grup", "group", "kategori", "category"),
)

private fun norm(s: String?): String =
    (s ?: "").trim().lowercase().replace(Regex("[\\s_-]+"), "")

/** Column indexes for phone / name / label / variable keys. `-1` = none. */
data class Mapping(
    var phone: Int = -1,
    var name: Int = -1,
    var label: Int = -1,
    val vars: MutableMap<String, Int> = mutableMapOf(),
)

/** Guess column indexes from header cells. */
fun guessMapping(headers: List<String>?, varKeys: List<String> = emptyList()): Mapping {
    val map = Mapping()
    val cells = (headers ?: emptyList()).map { norm(it) }
    map.phone = cells.indexOfFirst { it in HEADER_ALIASES.getValue("phone") }
    map.name = cells.indexOfFirst { it in HEADER_ALIASES.getValue("name") }
    map.label = cells.indexOfFirst { it in HEADER_ALIASES.getValue("label") }
    for (key in varKeys) {
        val target = norm(key)
        map.vars[key] = cells.withIndex().firstOrNull { (i, c) ->
            c == target && i != map.phone && i != map.name && i != map.label && i !in map.vars.values
        }?.index ?: -1
    }
    return map
}

data class ContactDraft(
    val phone: String,
    val name: String,
    val labels: List<String>,
    val values: Map<String, String>,
    val valid: Boolean,
)

/** Map parsed rows to contact drafts using a column mapping. */
fun mapRows(rows: List<List<String>>, mapping: Mapping, varKeys: List<String> = emptyList()): List<ContactDraft> =
    rows.map { row ->
        fun pick(i: Int): String = if (i in row.indices) row[i].trim() else ""
        val phone = Wa.normalizePhone(pick(mapping.phone))
        val labels = pick(mapping.label).split(Regex("[;|]")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val values = LinkedHashMap<String, String>()
        for (key in varKeys) {
            val value = pick(mapping.vars[key] ?: -1)
            if (value.isNotEmpty()) values[key] = value
        }
        ContactDraft(phone, pick(mapping.name), labels, values, Wa.isValidPhone(phone))
    }

/**
 * Parse pasted rows: `phone | name | value1 | value2 | ...`.
 * Delimiter may be `|`, tab or `,`. Columns 3+ fill the body's variables in order of appearance,
 * skipping the built-in `nama`. A row with only a phone is still valid and uses template globals.
 */
fun parsePasteRows(text: String?, varKeys: List<String> = emptyList()): List<ContactDraft> {
    val rows = parseDelimited(text, detectDelimiter(text, listOf('|', '\t', ',')))
    val valueKeys = varKeys.filter { it != "nama" }
    return rows.map { row ->
        fun pick(i: Int): String = if (i in row.indices) row[i].trim() else ""
        val phone = Wa.normalizePhone(pick(0))
        val values = LinkedHashMap<String, String>()
        for ((i, key) in valueKeys.withIndex()) {
            val value = pick(i + 2)
            if (value.isNotEmpty()) values[key] = value
        }
        ContactDraft(phone, pick(1), emptyList(), values, Wa.isValidPhone(phone))
    }
}

data class MergeResult(val added: Int, val merged: Int)/** Merge incoming contacts into local (dedup by phone). Local wins; empty fields are filled. */
fun mergeContacts(local: MutableList<Contact>, incoming: List<Contact>): MergeResult {
    val byPhone = local.associateByTo(HashMap()) { it.phone }
    var added = 0
    var merged = 0
    for (c in incoming) {
        val existing = byPhone[c.phone]
        if (existing == null) {
            local.add(c)
            byPhone[c.phone] = c
            added++
            continue
        }
        merged++
        if (existing.name.isEmpty() && c.name.isNotEmpty()) existing.name = c.name
        for (l in c.labels) if (l !in existing.labels) existing.labels.add(l)
        for ((tid, values) in c.values) {
            val target = existing.values.getOrPut(tid) { mutableMapOf() }
            for ((k, v) in values) if (target[k].isNullOrEmpty()) target[k] = v
        }
        for ((tid, whenSent) in c.sentAt) {
            val current = existing.sentAt[tid]
            if (current == null || current < whenSent) existing.sentAt[tid] = whenSent
        }
    }
    return MergeResult(added, merged)
}

/** Sorted unique labels across contacts. */
fun uniqueLabels(contacts: List<Contact>): List<String> {
    val set = sortedSetOf<String>(String.CASE_INSENSITIVE_ORDER)
    for (c in contacts) for (l in c.labels) if (l.isNotEmpty()) set.add(l)
    return set.toList()
}

/** Filter contacts by search query and label/group. */
fun filterContacts(
    contacts: List<Contact>,
    query: String = "",
    groupFilter: String = ""
): List<Contact> {
    val q = query.trim().lowercase()
    return contacts.filter { c ->
        val matchesGroup = when (groupFilter) {
            "", "__all__" -> true
            "__none__" -> c.labels.isEmpty()
            else -> c.labels.contains(groupFilter)
        }
        val matchesQuery = if (q.isEmpty()) {
            true
        } else {
            c.name.lowercase().contains(q) ||
            c.phone.lowercase().contains(q) ||
            c.labels.any { it.lowercase().contains(q) }
        }
        matchesGroup && matchesQuery
    }
}
