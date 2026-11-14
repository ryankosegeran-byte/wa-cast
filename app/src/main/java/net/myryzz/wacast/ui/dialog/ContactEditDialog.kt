package net.myryzz.wacast.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.Wa
import net.myryzz.wacast.core.expandLabels
import net.myryzz.wacast.core.syncGroups
import net.myryzz.wacast.core.uid
import net.myryzz.wacast.databinding.DialogContactEditBinding
import net.myryzz.wacast.model.Contact

class ContactEditDialog : DialogFragment() {

    private var _binding: DialogContactEditBinding? = null
    private val binding get() = _binding!!

    var contactId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogContactEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val state = AppRepository.state
        val existing = contactId?.let { cid -> state.contacts.firstOrNull { it.id == cid } }

        binding.btnCloseDialog.setOnClickListener { dismiss() }

        if (existing != null) {
            binding.tvDialogTitle.text = getString(R.string.edit_contact)
            binding.etContactPhone.setText(existing.phone)
            binding.etContactName.setText(existing.name)
            binding.etContactLabels.setText(existing.labels.joinToString(", "))
        } else {
            binding.tvDialogTitle.text = getString(R.string.new_contact)
        }

        // Available groups quick-add chips
        val groupChipContainer = binding.chipGroupAvailableLabels
        groupChipContainer.removeAllViews()
        syncGroups(state.groups, state.contacts)
        for (g in state.groups) {
            val chip = Chip(requireContext()).apply {
                text = "+ ${g.name}"
                isCheckable = false
                setOnClickListener {
                    val currentText = binding.etContactLabels.text?.toString() ?: ""
                    val currentList = currentText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    if (!currentList.contains(g.name)) {
                        currentList.add(g.name)
                        binding.etContactLabels.setText(currentList.joinToString(", "))
                    }
                }
            }
            groupChipContainer.addView(chip)
        }

        binding.btnSaveContact.setOnClickListener {
            val rawPhone = binding.etContactPhone.text?.toString()?.trim() ?: ""
            val normPhone = Wa.normalizePhone(rawPhone)

            if (!Wa.isValidPhone(normPhone)) {
                binding.tilContactPhone.error = getString(R.string.phone_invalid)
                return@setOnClickListener
            }
            binding.tilContactPhone.error = null

            // Check duplicate phone with other contacts
            val dup = state.contacts.firstOrNull { it.phone == normPhone && it.id != existing?.id }
            if (dup != null) {
                binding.tilContactPhone.error = getString(R.string.phone_exists)
                return@setOnClickListener
            }

            val name = binding.etContactName.text?.toString()?.trim() ?: ""
            val rawLabels = binding.etContactLabels.text?.toString() ?: ""
            val parsedLabels = rawLabels.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val expanded = expandLabels(state.groups, parsedLabels).toMutableList()

            if (existing != null) {
                existing.phone = normPhone
                existing.name = name
                existing.labels = expanded
            } else {
                val newContact = Contact(
                    id = uid("c"),
                    phone = normPhone,
                    name = name,
                    labels = expanded
                )
                state.contacts.add(newContact)
                if (AppRepository.previewContactId == null) {
                    AppRepository.previewContactId = newContact.id
                }
            }

            syncGroups(state.groups, state.contacts)
            AppRepository.scheduleSave()
            AppRepository.notifyChanged()
            AppRepository.postNotice(getString(R.string.contact_saved))
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ContactEditDialog"

        fun newInstance(contactId: String? = null): ContactEditDialog {
            val d = ContactEditDialog()
            d.contactId = contactId
            return d
        }
    }
}
