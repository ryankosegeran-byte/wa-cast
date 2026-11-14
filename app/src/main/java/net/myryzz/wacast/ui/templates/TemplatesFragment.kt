package net.myryzz.wacast.ui.templates

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.OutlineNode
import net.myryzz.wacast.core.filterTemplates
import net.myryzz.wacast.core.nowIso
import net.myryzz.wacast.core.templateGroupCounts
import net.myryzz.wacast.core.templateGroupDescendantIds
import net.myryzz.wacast.core.templateGroupOutline
import net.myryzz.wacast.core.uid
import net.myryzz.wacast.databinding.FragmentTemplatesBinding
import net.myryzz.wacast.databinding.ItemTemplateGroupBinding
import net.myryzz.wacast.databinding.ItemTemplateRowBinding
import net.myryzz.wacast.model.Template
import net.myryzz.wacast.ui.dialog.TemplateGroupManagerDialog

class TemplatesFragment : Fragment() {

    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!

    private var currentQuery: String = ""
    private var currentGroupFilter: String = ""
    private val expandedGroups = mutableMapOf<String, Boolean>()

    private val repositoryListener: () -> Unit = {
        activity?.runOnUiThread {
            if (_binding != null) {
                updateGroupFilterSpinner()
                renderList()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerTemplates.layoutManager = LinearLayoutManager(requireContext())

        setupSearch()
        setupButtons()
        setupGroupFilterSpinner()

        AppRepository.addListener(repositoryListener)
        renderList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AppRepository.removeListener(repositoryListener)
        _binding = null
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                currentQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                renderList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun setupButtons() {
        binding.btnNewTemplate.setOnClickListener { createNewTemplate() }
        binding.btnEmptyNew.setOnClickListener { createNewTemplate() }

        binding.btnManageGroups.setOnClickListener {
            TemplateGroupManagerDialog.newInstance()
                .show(childFragmentManager, TemplateGroupManagerDialog.TAG)
        }
    }

    private data class SpinnerItem(val id: String, val label: String) {
        override fun toString(): String = label
    }

    private fun setupGroupFilterSpinner() {
        updateGroupFilterSpinner()
        binding.spinnerGroupFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position) as? SpinnerItem ?: return
                if (currentGroupFilter != item.id) {
                    currentGroupFilter = item.id
                    renderList()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateGroupFilterSpinner() {
        val state = AppRepository.state
        val outline = templateGroupOutline(state.templateGroups)
        val counts = templateGroupCounts(state.templateGroups, state.templates)

        val items = mutableListOf<SpinnerItem>()
        items.add(SpinnerItem("", getString(R.string.all_groups)))
        for (g in outline) {
            val count = counts[g.id] ?: 0
            val prefix = "— ".repeat(g.depth)
            items.add(SpinnerItem(g.id, "$prefix${g.name} ($count)"))
        }
        val ungroupedCount = state.templates.count { it.groupId == null }
        items.add(SpinnerItem("__none__", "${getString(R.string.no_group)} ($ungroupedCount)"))

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
        binding.spinnerGroupFilter.adapter = adapter

        // Reselect current filter if still present
        val idx = items.indexOfFirst { it.id == currentGroupFilter }
        if (idx >= 0) binding.spinnerGroupFilter.setSelection(idx)
    }

    private fun createNewTemplate() {
        val now = nowIso()
        val targetGroupId = if (currentGroupFilter.isNotEmpty() && currentGroupFilter != "__none__" &&
            AppRepository.state.templateGroups.any { it.id == currentGroupFilter }
        ) {
            currentGroupFilter
        } else {
            null
        }

        val newTpl = Template(
            id = uid("tpl"),
            name = "",
            body = "",
            vars = mutableListOf(),
            globals = mutableMapOf(),
            auto = mutableMapOf(),
            groupId = targetGroupId,
            createdAt = now,
            updatedAt = now
        )

        AppRepository.state.templates.add(newTpl)
        AppRepository.activeTemplateId = newTpl.id
        AppRepository.scheduleSave()
        AppRepository.notifyChanged()

        // Navigate to Susun (Compose) tab
        navigateToComposeTab()
    }

    private fun selectTemplate(template: Template) {
        AppRepository.activeTemplateId = template.id
        AppRepository.notifyChanged()
        navigateToComposeTab()
    }

    private fun navigateToComposeTab() {
        activity?.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_compose
    }

    private fun confirmDeleteTemplate(template: Template) {
        val displayName = template.name.ifBlank { getString(R.string.untitled) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_template_confirm)
            .setMessage(getString(R.string.delete_template_desc) + "\n\nTemplate: $displayName")
            .setPositiveButton(R.string.delete) { _, _ ->
                val state = AppRepository.state
                state.templates.removeAll { it.id == template.id }
                for (contact in state.contacts) {
                    contact.values.remove(template.id)
                    contact.sentAt.remove(template.id)
                }
                if (AppRepository.activeTemplateId == template.id) {
                    AppRepository.activeTemplateId = state.templates.firstOrNull()?.id
                }
                AppRepository.scheduleSave()
                AppRepository.notifyChanged()
                AppRepository.postNotice(getString(R.string.template_deleted))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private sealed class ListItem {
        data class GroupHeader(
            val group: OutlineNode,
            val count: Int,
            val isExpanded: Boolean
        ) : ListItem()

        data class TemplateRow(
            val template: Template,
            val depth: Int,
            val isActive: Boolean
        ) : ListItem()
    }

    private fun renderList() {
        val state = AppRepository.state
        val allTemplates = state.templates
        val visibleTemplates = filterTemplates(allTemplates, state.templateGroups, currentQuery, currentGroupFilter)

        if (allTemplates.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = getString(R.string.no_templates)
            binding.tvEmptyDesc.text = getString(R.string.no_templates_desc)
            binding.recyclerTemplates.visibility = View.GONE
            return
        }

        if (visibleTemplates.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = getString(R.string.no_matching_templates)
            binding.tvEmptyDesc.text = ""
            binding.recyclerTemplates.visibility = View.GONE
            return
        }

        binding.emptyState.visibility = View.GONE
        binding.recyclerTemplates.visibility = View.VISIBLE

        val listItems = mutableListOf<ListItem>()
        val forceOpen = currentQuery.isNotEmpty() || (currentGroupFilter.isNotEmpty() && currentGroupFilter != "__none__")

        if (state.templateGroups.isEmpty()) {
            for (tpl in visibleTemplates) {
                listItems.add(ListItem.TemplateRow(tpl, depth = 0, isActive = tpl.id == AppRepository.activeTemplateId))
            }
        } else {
            val outline = templateGroupOutline(state.templateGroups)
            val counts = templateGroupCounts(state.templateGroups, state.templates)
            val tplByGroup = visibleTemplates.groupBy { it.groupId }

            for (group in outline) {
                val kids = tplByGroup[group.id] ?: emptyList()
                val hasKids = kids.isNotEmpty() || outline.any { it.parentId == group.id }
                if (!hasKids && currentQuery.isNotEmpty()) continue

                val isExpanded = if (forceOpen) true else expandedGroups[group.id] ?: true
                val groupCount = counts[group.id] ?: 0
                listItems.add(ListItem.GroupHeader(group, groupCount, isExpanded))

                if (isExpanded && kids.isNotEmpty()) {
                    for (tpl in kids) {
                        listItems.add(ListItem.TemplateRow(tpl, depth = group.depth + 1, isActive = tpl.id == AppRepository.activeTemplateId))
                    }
                }
            }

            // Ungrouped section
            val ungrouped = tplByGroup[null] ?: emptyList()
            if (ungrouped.isNotEmpty()) {
                val ungroupedHeader = OutlineNode(id = "__none__", name = getString(R.string.no_group), parentId = null, depth = 0)
                val isExpanded = if (forceOpen) true else expandedGroups["__none__"] ?: true
                listItems.add(ListItem.GroupHeader(ungroupedHeader, ungrouped.size, isExpanded))
                if (isExpanded) {
                    for (tpl in ungrouped) {
                        listItems.add(ListItem.TemplateRow(tpl, depth = 1, isActive = tpl.id == AppRepository.activeTemplateId))
                    }
                }
            }
        }

        binding.recyclerTemplates.adapter = TemplatesAdapter(
            items = listItems,
            onToggleGroup = { groupId ->
                val current = expandedGroups[groupId] ?: true
                expandedGroups[groupId] = !current
                renderList()
            },
            onSelectTemplate = { selectTemplate(it) },
            onDeleteTemplate = { confirmDeleteTemplate(it) }
        )
    }

    private class TemplatesAdapter(
        private val items: List<ListItem>,
        private val onToggleGroup: (String) -> Unit,
        private val onSelectTemplate: (Template) -> Unit,
        private val onDeleteTemplate: (Template) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is ListItem.GroupHeader -> VIEW_TYPE_GROUP
            is ListItem.TemplateRow -> VIEW_TYPE_TEMPLATE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_GROUP) {
                val binding = ItemTemplateGroupBinding.inflate(inflater, parent, false)
                GroupViewHolder(binding)
            } else {
                val binding = ItemTemplateRowBinding.inflate(inflater, parent, false)
                TemplateViewHolder(binding)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ListItem.GroupHeader -> (holder as GroupViewHolder).bind(item, onToggleGroup)
                is ListItem.TemplateRow -> (holder as TemplateViewHolder).bind(item, onSelectTemplate, onDeleteTemplate)
            }
        }

        override fun getItemCount(): Int = items.size

        class GroupViewHolder(val binding: ItemTemplateGroupBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: ListItem.GroupHeader, onToggle: (String) -> Unit) {
                val density = itemView.resources.displayMetrics.density
                binding.indentSpacer.layoutParams.width = (item.group.depth * 16 * density).toInt()
                binding.tvGroupName.text = item.group.name
                binding.tvGroupCount.text = item.count.toString()
                binding.ivChevron.rotation = if (item.isExpanded) 90f else 0f
                itemView.setOnClickListener { onToggle(item.group.id) }
            }
        }

        class TemplateViewHolder(val binding: ItemTemplateRowBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(
                item: ListItem.TemplateRow,
                onSelect: (Template) -> Unit,
                onDelete: (Template) -> Unit
            ) {
                val density = itemView.resources.displayMetrics.density
                binding.indentSpacer.layoutParams.width = (item.depth * 16 * density).toInt()

                val title = item.template.name.ifBlank { itemView.context.getString(R.string.untitled) }
                binding.tvTemplateName.text = title
                binding.tvTemplateSnippet.text = item.template.body.replace("\n", " ").trim()
                binding.tvTemplateSnippet.visibility = if (item.template.body.isNotBlank()) View.VISIBLE else View.GONE

                if (item.isActive) {
                    binding.tvTemplateName.setTextColor(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.primary))
                } else {
                    binding.tvTemplateName.setTextColor(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.black))
                }

                itemView.setOnClickListener { onSelect(item.template) }
                binding.btnDelete.setOnClickListener { onDelete(item.template) }
            }
        }

        companion object {
            const val VIEW_TYPE_GROUP = 1
            const val VIEW_TYPE_TEMPLATE = 2
        }
    }

    companion object {
        fun newInstance() = TemplatesFragment()
    }
}
