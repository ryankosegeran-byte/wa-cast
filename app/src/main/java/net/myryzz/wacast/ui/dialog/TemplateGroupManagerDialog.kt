package net.myryzz.wacast.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.OutlineNode
import net.myryzz.wacast.core.createTemplateGroup
import net.myryzz.wacast.core.deleteTemplateGroup
import net.myryzz.wacast.core.renameTemplateGroup
import net.myryzz.wacast.core.templateGroupCounts
import net.myryzz.wacast.core.templateGroupOutline
import net.myryzz.wacast.databinding.DialogTemplateGroupManagerBinding
import net.myryzz.wacast.databinding.ItemManageGroupRowBinding

class TemplateGroupManagerDialog : DialogFragment() {

    private var _binding: DialogTemplateGroupManagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTemplateGroupManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener { dismiss() }

        binding.recyclerGroups.layoutManager = LinearLayoutManager(requireContext())
        refreshGroupsList()

        binding.btnAddGroup.setOnClickListener {
            val name = binding.etGroupName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) return@setOnClickListener

            val state = AppRepository.state
            if (state.templateGroups.any { it.name.equals(name, ignoreCase = true) }) {
                Toast.makeText(requireContext(), getString(R.string.group_name_exists), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createTemplateGroup(state.templateGroups, name, null)
            binding.etGroupName.setText("")
            AppRepository.scheduleSave()
            AppRepository.notifyChanged()
            refreshGroupsList()
        }
    }

    private fun refreshGroupsList() {
        val state = AppRepository.state
        val outline = templateGroupOutline(state.templateGroups)
        val counts = templateGroupCounts(state.templateGroups, state.templates)

        binding.recyclerGroups.adapter = GroupAdapter(
            outline = outline,
            counts = counts,
            onRename = { group -> promptRenameGroup(group) },
            onDelete = { group -> confirmDeleteGroup(group) }
        )
    }

    private fun promptRenameGroup(group: OutlineNode) {
        val input = EditText(requireContext())
        input.setText(group.name)
        input.setSelection(group.name.length)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.group_name)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != group.name) {
                    val state = AppRepository.state
                    val ok = renameTemplateGroup(state.templateGroups, group.name, newName)
                    if (ok) {
                        AppRepository.scheduleSave()
                        AppRepository.notifyChanged()
                        refreshGroupsList()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.group_name_exists), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteGroup(group: OutlineNode) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage("Hapus grup '${group.name}'? Template di dalamnya akan dipindahkan ke grup induk.")
            .setPositiveButton(R.string.delete) { _, _ ->
                val state = AppRepository.state
                val moved = deleteTemplateGroup(state.templateGroups, state.templates, group.id)
                AppRepository.scheduleSave()
                AppRepository.notifyChanged()
                if (moved > 0) {
                    AppRepository.postNotice(getString(R.string.templates_moved, moved))
                }
                refreshGroupsList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private class GroupAdapter(
        private val outline: List<OutlineNode>,
        private val counts: Map<String, Int>,
        private val onRename: (OutlineNode) -> Unit,
        private val onDelete: (OutlineNode) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemManageGroupRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemManageGroupRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = outline[position]
            holder.binding.indentSpacer.layoutParams.width = (item.depth * 20 * holder.itemView.resources.displayMetrics.density).toInt()
            holder.binding.tvName.text = item.name
            holder.binding.tvCount.text = (counts[item.id] ?: 0).toString()

            holder.binding.btnEdit.setOnClickListener { onRename(item) }
            holder.binding.btnDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount(): Int = outline.size
    }

    companion object {
        const val TAG = "TemplateGroupManagerDialog"
        fun newInstance() = TemplateGroupManagerDialog()
    }
}
