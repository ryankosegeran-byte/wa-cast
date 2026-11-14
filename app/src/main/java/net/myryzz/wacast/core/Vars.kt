package net.myryzz.wacast.core

import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Template

/** Matches `{{ key }}` tokens. Mirrors the web app's `VAR_RE`. */
private val VAR_RE = Regex("""\{\{\s*([\w.-]+)\s*}}""")

private val VAR_KEY_RE = Regex("""^[\w.-]+$""")

/** Keys detected in a body, unique, in order of first appearance. */
fun detectVariables(body: String?): List<String> {
    val out = LinkedHashSet<String>()
    for (match in VAR_RE.findAll(body ?: "")) out.add(match.groupValues[1])
    return out.toList()
}

/** First non-empty wins: per-contact value, contact name, auto date, template globals. */
fun resolve(key: String, contact: Contact?, template: Template?, lang: String = "id"): String {
    val own = template?.let { t -> contact?.values?.get(t.id)?.get(key) }
    if (!own.isNullOrEmpty()) return own
    if (key == "nama" && !contact?.name.isNullOrEmpty()) return contact.name
    val preset = template?.auto?.get(key)
    if (isAutoPreset(preset)) return formatAutoDate(preset, lang = lang)
    val global = template?.globals?.get(key)
    if (!global.isNullOrEmpty()) return global
    return ""
}

/** Render a template body against a contact, filling every `{{key}}` token. */
fun render(body: String?, contact: Contact?, template: Template?, lang: String = "id"): String =
    VAR_RE.replace(body ?: "") { match -> resolve(match.groupValues[1], contact, template, lang) }

/** Keys used by a template: declared vars first, then keys found in the body. */
fun templateVarKeys(template: Template?): List<String> {
    val declared = (template?.vars ?: emptyList())
        .filter { VAR_KEY_RE.matches(it.key) }
        .map { it.key }
    return (declared + detectVariables(template?.body)).distinct()
}

/** Display name for a variable, falling back to the key itself. */
fun varLabel(template: Template?, key: String): String {
    val label = template?.vars?.firstOrNull { it.key == key }?.label?.trim()
    return if (!label.isNullOrEmpty()) label else key
}

/** Variables appearing in body that resolve to an empty string. */
fun unresolvedVariables(body: String?, contact: Contact?, template: Template?, lang: String = "id"): List<String> {
    val detected = detectVariables(body)
    return detected.filter { key ->
        resolve(key, contact, template, lang).isEmpty()
    }
}
