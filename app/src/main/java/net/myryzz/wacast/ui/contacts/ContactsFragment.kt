package net.myryzz.wacast.ui.contacts

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.filterContacts
import net.myryzz.wacast.core.groupOutline
import net.myryzz.wacast.core.syncGroups
import net.myryzz.wacast.databinding.FragmentContactsBinding
import net.myryzz.wacast.databinding.ItemContactRowBinding
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.ui.dialog.ContactEditDialog
import net.myryzz.wacast.ui.dialog.ContactGroupManagerDialog

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!

    private var currentQuery = ""
    private var currentGroupFilter = ""
    private val selectedContactIds = mutableSetOf<String>()

    private val repositoryListener: () -> Unit = {
        activity?.runOnUiThread {
            if (_binding != null) {
                updateGroupFilterSpinner()
                renderContactsList()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerContacts.layoutManager = LinearLayoutManager(requireContext())

        setupSearch()
        setupButtons()
        setupBatchActions()
        setupGroupFilterSpinner()

        AppRepository.addListener(repositoryListener)
        renderContactsList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AppRepository.removeListener(repositoryListener)
        _binding = null
    }

    private fun setupSearch() {
        binding.etContactSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                currentQuery = s?.toString()?.trim() ?: ""
                binding.btnClearContactSearch.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                renderContactsList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearContactSearch.setOnClickListener {
            binding.etContactSearch.setText("")
        }
    }

    private fun setupButtons() {
        binding.btnNewContact.setOnClickListener {
            ContactEditDialog.newInstance().show(childFragmentManager, ContactEditDialog.TAG)
        }
        binding.btnEmptyAddContact.setOnClickListener {
            ContactEditDialog.newInstance().show(childFragmentManager, ContactEditDialog.TAG)
        }
        binding.btnManageContactGroups.setOnClickListener {
            ContactGroupManagerDialog.newInstance().show(childFragmentManager, ContactGroupManagerDialog.TAG)
        }
    }

    private fun setupBatchActions() {
        binding.btnBatchClose.setOnClickListener {
            selectedContactIds.clear()
            updateBatchActionBar()
            renderContactsList()
        }

        binding.btnBatchSelectAll.setOnClickListener {
            val visible = filterContacts(AppRepository.state.contacts, currentQuery, currentGroupFilter)
            selectedContactIds.addAll(visible.map { it.id })
            updateBatchActionBar()
            renderContactsList()
        }

        binding.btnBatchDelete.setOnClickListener {
            if (selectedContactIds.isEmpty()) return@setOnClickListener
            val count = selectedContactIds.size
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.confirm_batch_delete, count))
                .setPositiveButton(R.string.delete) { _, _ ->
                    val state = AppRepository.state
                    state.contacts.removeAll { it.id in selectedContactIds }
                    if (AppRepository.previewContactId in selectedContactIds) {
                        AppRepository.previewContactId = state.contacts.firstOrNull()?.id
                    }
                    selectedContactIds.clear()
                    updateBatchActionBar()
                    AppRepository.scheduleSave()
                    AppRepository.notifyChanged()
                    AppRepository.postNotice(getString(R.string.batch_deleted, count))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updateBatchActionBar() {
        if (selectedContactIds.isNotEmpty()) {
            binding.batchActionBar.visibility = View.VISIBLE
            binding.tvBatchSelectedCount.text = getString(R.string.selected_count, selectedContactIds.size)
        } else {
            binding.batchActionBar.visibility = View.GONE
        }
    }

    private data class FilterItem(val id: String, val label: String) {
        override fun toString(): String = label
    }

    private fun setupGroupFilterSpinner() {
        updateGroupFilterSpinner()
        binding.spinnerContactGroupFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = parent?.getItemAtPosition(position) as? FilterItem ?: return
                if (currentGroupFilter != item.id) {
                    currentGroupFilter = item.id
                    renderContactsList()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateGroupFilterSpinner() {
        val state = AppRepository.state
        syncGroups(state.groups, state.contacts)
        val outline = groupOutline(state.groups)

        val items = mutableListOf<FilterItem>()
        items.add(FilterItem("", getString(R.string.all_labels)))
        for (g in outline) {
            val count = state.contacts.count { it.labels.contains(g.name) }
            val prefix = "— ".repeat(g.depth)
            items.add(FilterItem(g.name, "$prefix${g.name} ($count)"))
        }
        val ungroupedCount = state.contacts.count { it.labels.isEmpty() }
        items.add(FilterItem("__none__", "${getString(R.string.no_label)} ($ungroupedCount)"))

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
        binding.spinnerContactGroupFilter.adapter = adapter

        val idx = items.indexOfFirst { it.id == currentGroupFilter }
        if (idx >= 0) binding.spinnerContactGroupFilter.setSelection(idx)
    }

    private fun confirmDeleteSingleContact(contact: Contact) {
        val displayName = contact.name.ifBlank { contact.phone }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_contact_confirm)
            .setMessage(getString(R.string.delete_contact_desc, displayName, contact.phone))
            .setPositiveButton(R.string.delete) { _, _ ->
                val state = AppRepository.state
                state.contacts.removeAll { it.id == contact.id }
                selectedContactIds.remove(contact.id)
                updateBatchActionBar()
                if (AppRepository.previewContactId == contact.id) {
                    AppRepository.previewContactId = state.contacts.firstOrNull()?.id
                }
                AppRepository.scheduleSave()
                AppRepository.notifyChanged()
                AppRepository.postNotice(getString(R.string.contact_deleted))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderContactsList() {
        val allContacts = AppRepository.state.contacts
        val visibleContacts = filterContacts(allContacts, currentQuery, currentGroupFilter)

        if (allContacts.isEmpty()) {
            binding.contactsEmptyState.visibility = View.VISIBLE
            binding.tvEmptyContactsTitle.text = getString(R.string.no_contacts)
            binding.tvEmptyContactsDesc.text = getString(R.string.no_contacts_desc)
            binding.recyclerContacts.visibility = View.GONE
            return
        }

        if (visibleContacts.isEmpty()) {
            binding.contactsEmptyState.visibility = View.VISIBLE
            binding.tvEmptyContactsTitle.text = getString(R.string.no_matching_contacts)
            binding.tvEmptyContactsDesc.text = ""
            binding.recyclerContacts.visibility = View.GONE
            return
        }

        binding.contactsEmptyState.visibility = View.GONE
        binding.recyclerContacts.visibility = View.VISIBLE

        val activeTplId = AppRepository.activeTemplateId

        binding.recyclerContacts.adapter = ContactsAdapter(
            contacts = visibleContacts,
            selectedIds = selectedContactIds,
            activeTemplateId = activeTplId,
            onToggleSelect = { contactId, isChecked ->
                if (isChecked) selectedContactIds.add(contactId) else selectedContactIds.remove(contactId)
                updateBatchActionBar()
            },
            onEditContact = { contact ->
                ContactEditDialog.newInstance(contact.id).show(childFragmentManager, ContactEditDialog.TAG)
            },
            onDeleteContact = { contact ->
                confirmDeleteSingleContact(contact)
            }
        )
    }

    private class ContactsAdapter(
        private val contacts: List<Contact>,
        private val selectedIds: Set<String>,
        private val activeTemplateId: String?,
        private val onToggleSelect: (String, Boolean) -> Unit,
        private val onEditContact: (Contact) -> Unit,
        private val onDeleteContact: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemContactRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemContactRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val c = contacts[position]
            val b = holder.binding

            val displayName = c.name.ifBlank { c.phone }
            b.tvName.text = displayName
            b.tvPhone.text = c.phone
            b.tvAvatar.text = displayName.take(1).uppercase()

            if (c.labels.isNotEmpty()) {
                b.tvLabels.text = c.labels.joinToString(" • ")
                b.tvLabels.visibility = View.VISIBLE
            } else {
                b.tvLabels.visibility = View.GONE
            }

            // Check if sent for active template
            val isSent = activeTemplateId != null && c.sentAt.containsKey(activeTemplateId)
            b.tvSentBadge.visibility = if (isSent) View.VISIBLE else View.GONE

            // Multi-selection state
            b.cbSelectContact.setOnCheckedChangeListener(null)
            b.cbSelectContact.isChecked = selectedIds.contains(c.id)
            b.cbSelectContact.setOnCheckedChangeListener { _, isChecked ->
                onToggleSelect(c.id, isChecked)
            }

            holder.itemView.setOnClickListener {
                val nextState = !b.cbSelectContact.isChecked
                b.cbSelectContact.isChecked = nextState
                onToggleSelect(c.id, nextState)
            }

            b.btnEditContact.setOnClickListener { onEditContact(c) }
            b.btnDeleteContact.setOnClickListener { onDeleteContact(c) }
        }

        override fun getItemCount(): Int = contacts.size
    }

    companion object {
        fun newInstance() = ContactsFragment()
    }
}
