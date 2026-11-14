package net.myryzz.wacast.ui.compose

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.AUTO_PRESETS
import net.myryzz.wacast.core.formatAutoDate
import net.myryzz.wacast.core.isAutoPreset
import net.myryzz.wacast.core.nowIso
import net.myryzz.wacast.core.templateGroupOutline
import net.myryzz.wacast.core.templateVarKeys
import net.myryzz.wacast.core.uid
import net.myryzz.wacast.core.wrapMarker
import net.myryzz.wacast.databinding.FragmentComposeBinding
import net.myryzz.wacast.databinding.ItemComposeVarRowBinding
import net.myryzz.wacast.model.Template
import net.myryzz.wacast.model.TemplateVar
import net.myryzz.wacast.ui.dialog.AutoDateDialog
import net.myryzz.wacast.ui.dialog.EmojiPickerDialog
import java.util.Date

class ComposeFragment : Fragment() {

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    private var isInternalUpdate = false
    private var lastLoadedTemplateId: String? = null

    private val repositoryListener: () -> Unit = {
        activity?.runOnUiThread {
            if (_binding != null) {
                renderAll()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerVariables.layoutManager = LinearLayoutManager(requireContext())

        setupTopBar()
        setupEditorInputs()
        setupFormatToolbar()
        setupVariablesSection()

        AppRepository.addListener(repositoryListener)
        renderAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AppRepository.removeListener(repositoryListener)
        _binding = null
    }

    private fun getActiveTemplate(): Template? {
        val state = AppRepository.state
        val activeId = AppRepository.activeTemplateId
        var tpl = state.templates.firstOrNull { it.id == activeId }
        if (tpl == null && state.templates.isNotEmpty()) {
            tpl = state.templates.first()
            AppRepository.activeTemplateId = tpl.id
        }
        return tpl
    }

    private data class SpinnerOption(val id: String, val label: String) {
        override fun toString(): String = label
    }

    private fun setupTopBar() {
        binding.btnComposeNewTemplate.setOnClickListener { createNewTemplate() }
        binding.btnEmptyCreateTemplate.setOnClickListener { createNewTemplate() }

        binding.spinnerActiveTemplate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInternalUpdate) return
                val item = parent?.getItemAtPosition(position) as? SpinnerOption ?: return
                if (AppRepository.activeTemplateId != item.id) {
                    AppRepository.activeTemplateId = item.id
                    lastLoadedTemplateId = null
                    renderAll()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerAssignGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInternalUpdate) return
                val tpl = getActiveTemplate() ?: return
                val item = parent?.getItemAtPosition(position) as? SpinnerOption ?: return
                val targetGroupId = if (item.id.isEmpty() || item.id == "__none__") null else item.id
                if (tpl.groupId != targetGroupId) {
                    tpl.groupId = targetGroupId
                    tpl.updatedAt = nowIso()
                    AppRepository.scheduleSave()
                    AppRepository.notifyChanged()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun createNewTemplate() {
        val now = nowIso()
        val newTpl = Template(
            id = uid("tpl"),
            name = "",
            body = "",
            vars = mutableListOf(),
            globals = mutableMapOf(),
            auto = mutableMapOf(),
            groupId = null,
            createdAt = now,
            updatedAt = now
        )
        AppRepository.state.templates.add(newTpl)
        AppRepository.activeTemplateId = newTpl.id
        AppRepository.scheduleSave()
        AppRepository.notifyChanged()
        renderAll()
    }

    private fun setupEditorInputs() {
        binding.etTemplateName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isInternalUpdate) return
                val tpl = getActiveTemplate() ?: return
                val text = s?.toString() ?: ""
                if (tpl.name != text) {
                    tpl.name = text
                    tpl.updatedAt = nowIso()
                    AppRepository.scheduleSave()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etMessageBody.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isInternalUpdate) return
                val tpl = getActiveTemplate() ?: return
                val text = s?.toString() ?: ""
                if (tpl.body != text) {
                    tpl.body = text
                    tpl.updatedAt = nowIso()
                    AppRepository.scheduleSave()
                    renderVariables()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFormatToolbar() {
        binding.btnFormatBold.setOnClickListener { applyFormat("*") }
        binding.btnFormatItalic.setOnClickListener { applyFormat("_") }
        binding.btnFormatStrike.setOnClickListener { applyFormat("~") }
        binding.btnFormatMono.setOnClickListener { applyFormat("`") }

        binding.btnToolbarEmoji.setOnClickListener {
            EmojiPickerDialog.newInstance { emoji ->
                insertAtCursor(emoji)
            }.show(childFragmentManager, EmojiPickerDialog.TAG)
        }
    }

    private fun applyFormat(marker: String) {
        val et = binding.etMessageBody
        val text = et.text?.toString() ?: ""
        val start = et.selectionStart.coerceIn(0, text.length)
        val end = et.selectionEnd.coerceIn(start, text.length)
        val res = wrapMarker(text, start, end, marker)

        isInternalUpdate = true
        et.setText(res.text)
        isInternalUpdate = false

        val s = res.start.coerceIn(0, res.text.length)
        val e = res.end.coerceIn(s, res.text.length)
        et.setSelection(s, e)
        et.requestFocus()

        val tpl = getActiveTemplate() ?: return
        tpl.body = res.text
        tpl.updatedAt = nowIso()
        AppRepository.scheduleSave()
        renderVariables()
    }

    private fun insertAtCursor(text: String) {
        val et = binding.etMessageBody
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        et.text?.replace(start, end, text)
        et.setSelection(start + text.length)
        et.requestFocus()
    }

    private fun setupVariablesSection() {
        binding.btnAddVar.setOnClickListener {
            val raw = binding.etAddVar.text?.toString()?.trim() ?: ""
            if (raw.isEmpty()) return@setOnClickListener

            val key = raw.replace(Regex("""[^\w.-]"""), "")
            if (key.isEmpty()) {
                Toast.makeText(requireContext(), "Gunakan karakter huruf, angka, titik, atau strip.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tpl = getActiveTemplate() ?: return@setOnClickListener
            if (tpl.vars.none { it.key == key }) {
                tpl.vars.add(TemplateVar(key = key, label = ""))
                tpl.updatedAt = nowIso()
                AppRepository.scheduleSave()
            }
            binding.etAddVar.setText("")
            renderVariables()
        }
    }

    private fun renderAll() {
        val tpl = getActiveTemplate()
        if (tpl == null) {
            binding.templateSelectorBar.visibility = View.GONE
            binding.editorScrollView.visibility = View.GONE
            binding.composeEmptyState.visibility = View.VISIBLE
            return
        }

        binding.templateSelectorBar.visibility = View.VISIBLE
        binding.editorScrollView.visibility = View.VISIBLE
        binding.composeEmptyState.visibility = View.GONE

        isInternalUpdate = true

        // 1. Template Selector Spinner
        val templates = AppRepository.state.templates
        val tplOptions = templates.map {
            val title = it.name.ifBlank { getString(R.string.untitled) }
            SpinnerOption(it.id, title)
        }
        val tplAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tplOptions)
        binding.spinnerActiveTemplate.adapter = tplAdapter
        val currentTplIdx = tplOptions.indexOfFirst { it.id == tpl.id }
        if (currentTplIdx >= 0) binding.spinnerActiveTemplate.setSelection(currentTplIdx)

        // 2. Group Assignment Spinner
        val groupOutline = templateGroupOutline(AppRepository.state.templateGroups)
        val groupOptions = mutableListOf<SpinnerOption>()
        groupOptions.add(SpinnerOption("__none__", getString(R.string.no_group)))
        for (g in groupOutline) {
            val prefix = "— ".repeat(g.depth)
            groupOptions.add(SpinnerOption(g.id, "$prefix${g.name}"))
        }
        val groupAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, groupOptions)
        binding.spinnerAssignGroup.adapter = groupAdapter
        val currentGroupIdx = groupOptions.indexOfFirst { it.id == (tpl.groupId ?: "__none__") }
        if (currentGroupIdx >= 0) binding.spinnerAssignGroup.setSelection(currentGroupIdx)

        // 3. Name & Body Fields (only if template switched or text differs)
        if (lastLoadedTemplateId != tpl.id) {
            binding.etTemplateName.setText(tpl.name)
            binding.etMessageBody.setText(tpl.body)
            lastLoadedTemplateId = tpl.id
        } else {
            if (binding.etTemplateName.text?.toString() != tpl.name) {
                binding.etTemplateName.setText(tpl.name)
            }
            if (binding.etMessageBody.text?.toString() != tpl.body) {
                binding.etMessageBody.setText(tpl.body)
            }
        }

        isInternalUpdate = false

        renderVariables()
    }

    private fun renderVariables() {
        val tpl = getActiveTemplate() ?: return
        val allKeys = templateVarKeys(tpl)

        // 1. Render Chips
        val chipGroup = binding.chipGroupVars
        chipGroup.removeAllViews()

        // Built-in {{nama}} chip
        val namaChip = Chip(requireContext()).apply {
            text = "+ {{nama}}"
            isCheckable = false
            setOnClickListener { insertAtCursor("{{nama}}") }
        }
        chipGroup.addView(namaChip)

        for (k in allKeys) {
            val chip = Chip(requireContext()).apply {
                text = "+ {{$k}}"
                isCheckable = false
                setOnClickListener { insertAtCursor("{{$k}}") }
            }
            chipGroup.addView(chip)
        }

        // 2. Render Variables List
        binding.recyclerVariables.adapter = VariablesAdapter(
            template = tpl,
            keys = allKeys,
            onUpdateLabel = { key, label ->
                val v = tpl.vars.firstOrNull { it.key == key }
                if (v != null) {
                    v.label = label
                } else {
                    tpl.vars.add(TemplateVar(key, label))
                }
                tpl.updatedAt = nowIso()
                AppRepository.scheduleSave()
            },
            onUpdateDefault = { key, defVal ->
                if (defVal.isEmpty()) {
                    tpl.globals.remove(key)
                } else {
                    tpl.globals[key] = defVal
                }
                tpl.updatedAt = nowIso()
                AppRepository.scheduleSave()
            },
            onDeleteVar = { key ->
                tpl.vars.removeAll { it.key == key }
                tpl.globals.remove(key)
                tpl.auto.remove(key)
                val esc = Regex.escape(key)
                tpl.body = Regex("""\{\{\s*$esc\s*\}\}""").replace(tpl.body, "")
                tpl.updatedAt = nowIso()
                AppRepository.scheduleSave()

                isInternalUpdate = true
                binding.etMessageBody.setText(tpl.body)
                isInternalUpdate = false

                renderVariables()
            },
            onOpenAutoDate = { key ->
                AutoDateDialog.newInstance(
                    varKey = key,
                    currentPreset = tpl.auto[key]
                ) { chosenPreset ->
                    if (chosenPreset == null) {
                        tpl.auto.remove(key)
                    } else {
                        tpl.auto[key] = chosenPreset
                    }
                    tpl.updatedAt = nowIso()
                    AppRepository.scheduleSave()
                    renderVariables()
                }.show(childFragmentManager, AutoDateDialog.TAG)
            },
            onClearAutoDate = { key ->
                tpl.auto.remove(key)
                tpl.updatedAt = nowIso()
                AppRepository.scheduleSave()
                renderVariables()
            }
        )
    }

    private class VariablesAdapter(
        private val template: Template,
        private val keys: List<String>,
        private val onUpdateLabel: (String, String) -> Unit,
        private val onUpdateDefault: (String, String) -> Unit,
        private val onDeleteVar: (String) -> Unit,
        private val onOpenAutoDate: (String) -> Unit,
        private val onClearAutoDate: (String) -> Unit
    ) : RecyclerView.Adapter<VariablesAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemComposeVarRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemComposeVarRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val key = keys[position]
            val b = holder.binding

            b.tvVarToken.text = "{{$key}}"

            val currentLabel = template.vars.firstOrNull { it.key == key }?.label ?: ""
            b.etVarLabel.setText(currentLabel)

            val currentDefault = template.globals[key] ?: ""
            b.etVarDefault.setText(currentDefault)

            val autoPreset = template.auto[key]
            val hasAuto = isAutoPreset(autoPreset)

            if (hasAuto) {
                b.tilVarDefault.visibility = View.GONE
                b.btnAutoValuePreview.visibility = View.VISIBLE
                val formatted = formatAutoDate(autoPreset, Date(), AppRepository.lang)
                b.btnAutoValuePreview.text = formatted
                b.btnAutoValuePreview.setOnClickListener { onOpenAutoDate(key) }

                b.tvAutoBadge.visibility = View.VISIBLE
                val badgeRes = when (autoPreset) {
                    "dateMonthYear" -> R.string.auto_preset_dateMonthYear
                    "monthName" -> R.string.auto_preset_monthName
                    "year" -> R.string.auto_preset_year
                    "monthNum" -> R.string.auto_preset_monthNum
                    "monthYear" -> R.string.auto_preset_monthYear
                    "dayName" -> R.string.auto_preset_dayName
                    "numericDate" -> R.string.auto_preset_numericDate
                    else -> R.string.auto_none
                }
                b.tvAutoBadge.text = b.root.context.getString(badgeRes)
                b.btnClearAuto.visibility = View.VISIBLE
                b.btnClearAuto.setOnClickListener { onClearAutoDate(key) }
            } else {
                b.tilVarDefault.visibility = View.VISIBLE
                b.btnAutoValuePreview.visibility = View.GONE
                b.tvAutoBadge.visibility = View.GONE
                b.btnClearAuto.visibility = View.GONE
            }

            b.btnSetAutoDate.setOnClickListener { onOpenAutoDate(key) }
            b.btnDeleteVar.setOnClickListener { onDeleteVar(key) }

            // Attach listeners to input fields
            b.etVarLabel.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    onUpdateLabel(key, s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            b.etVarDefault.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    onUpdateDefault(key, s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        override fun getItemCount(): Int = keys.size
    }

    companion object {
        fun newInstance() = ComposeFragment()
    }
}
