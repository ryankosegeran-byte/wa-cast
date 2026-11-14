package net.myryzz.wacast.model

/** A variable declared on a template (key is the `{{key}}` token, label is the optional display name). */
data class TemplateVar(
    val key: String,
    var label: String = "",
)

data class Template(
    val id: String,
    var name: String = "",
    var body: String = "",
    var vars: MutableList<TemplateVar> = mutableListOf(),
    /** key -> auto date preset (see AUTO_PRESETS). */
    var auto: MutableMap<String, String> = mutableMapOf(),
    /** key -> default value used when a contact has no own value. */
    var globals: MutableMap<String, String> = mutableMapOf(),
    var groupId: String? = null,
    var createdAt: String = "",
    var updatedAt: String = "",
)

data class Contact(
    val id: String = "",
    var phone: String = "",
    var name: String = "",
    var labels: MutableList<String> = mutableListOf(),
    /** templateId -> (key -> value). */
    var values: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
    /** templateId -> ISO timestamp of the local "opened" mark. Not proof of delivery. */
    var sentAt: MutableMap<String, String> = mutableMapOf(),
)

/** Group / subgroup node, shared by contacts (labels) and templates. */
data class Group(
    val id: String,
    var name: String,
    var parentId: String? = null,
)

/** Whole persisted document. Shape matches the web app's `wa-broadcast:v1` payload. */
data class AppState(
    var schemaVersion: Int = 5,
    var updatedAt: String = "",
    var groups: MutableList<Group> = mutableListOf(),
    var templateGroups: MutableList<Group> = mutableListOf(),
    var templates: MutableList<Template> = mutableListOf(),
    var contacts: MutableList<Contact> = mutableListOf(),
)
