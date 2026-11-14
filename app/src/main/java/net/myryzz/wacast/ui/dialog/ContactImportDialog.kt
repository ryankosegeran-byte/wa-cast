package net.myryzz.wacast.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.myryzz.wacast.R
import net.myryzz.wacast.core.ContactDraft
import net.myryzz.wacast.core.Mapping
import net.myryzz.wacast.core.mapRows
import net.myryzz.wacast.core.uid
import net.myryzz.wacast.databinding.DialogContactImportBinding
import net.myryzz.wacast.databinding.ItemImportPreviewRowBinding
import net.myryzz.wacast.model.Contact

class ContactImportDialog : DialogFragment() {

    private var _binding: DialogContactImportBinding? = null
    private val binding get() = _binding!!

    var fileName: String = ""
    var headers: List<String> = emptyList()
    var dataRows: List<List<String>> = emptyList()
    var mapping: Mapping = Mapping()
    var templateId: String? = null
    var varKeys: List<String> = emptyList()

    var onImportConfirmed: ((List<Contact>, Int) -> Unit)? = null

    private data class ColOption(val index: Int, val label: String) {
        override fun toString(): String = label
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogContactImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseImport.setOnClickListener { dismiss() }
        binding.btnCancelImport.setOnClickListener { dismiss() }

        binding.tvImportFilename.text = "$fileName · ${dataRows.size} baris"
        binding.recyclerImportPreview.layoutManager = LinearLayoutManager(requireContext())

        setupSpinners()
        updatePreview()

        binding.btnDoImport.setOnClickListener {
            if (mapping.phone < 0) return@setOnClickListener
            val mapped = mapRows(dataRows, mapping, varKeys)
            val validDrafts = mapped.filter { it.valid }
            val badCount = mapped.size - validDrafts.size

            val contacts = validDrafts.map { draft ->
                val vals = if (!templateId.isNullOrEmpty() && draft.values.isNotEmpty()) {
                    mutableMapOf(templateId!! to draft.values.toMutableMap())
                } else {
                    mutableMapOf()
                }
                Contact(
                    id = uid("c"),
                    phone = draft.phone,
                    name = draft.name,
                    labels = draft.labels.toMutableList(),
                    values = vals,
                    sentAt = mutableMapOf()
                )
            }

            onImportConfirmed?.invoke(contacts, badCount)
            dismiss()
        }
    }

    private fun buildColOptions(): List<ColOption> {
        val list = mutableListOf<ColOption>()
        list.add(ColOption(-1, getString(R.string.import_map_ignore)))
        for (i in headers.indices) {
            val name = headers[i].ifBlank { getString(R.string.import_map_column, i + 1) }
            list.add(ColOption(i, "#${i + 1}: $name"))
        }
        return list
    }

    private fun setupSpinners() {
        val options = buildColOptions()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)

        // 1. Phone Spinner
        binding.spinnerMapPhone.adapter = adapter
        val phoneIdx = options.indexOfFirst { it.index == mapping.phone }
        if (phoneIdx >= 0) binding.spinnerMapPhone.setSelection(phoneIdx)
        binding.spinnerMapPhone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val opt = parent?.getItemAtPosition(position) as? ColOption ?: return
                if (mapping.phone != opt.index) {
                    mapping.phone = opt.index
                    updatePreview()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 2. Name Spinner
        binding.spinnerMapName.adapter = adapter
        val nameIdx = options.indexOfFirst { it.index == mapping.name }
        if (nameIdx >= 0) binding.spinnerMapName.setSelection(nameIdx)
        binding.spinnerMapName.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val opt = parent?.getItemAtPosition(position) as? ColOption ?: return
                if (mapping.name != opt.index) {
                    mapping.name = opt.index
                    updatePreview()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Label Spinner
        binding.spinnerMapLabel.adapter = adapter
        val labelIdx = options.indexOfFirst { it.index == mapping.label }
        if (labelIdx >= 0) binding.spinnerMapLabel.setSelection(labelIdx)
        binding.spinnerMapLabel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val opt = parent?.getItemAtPosition(position) as? ColOption ?: return
                if (mapping.label != opt.index) {
                    mapping.label = opt.index
                    updatePreview()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. Dynamic Variable Mapping Spinners
        val varContainer = binding.containerVariableMappings
        varContainer.removeAllViews()
        for (vKey in varKeys) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * resources.displayMetrics.density).toInt() }
            }

            val tv = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "{{$vKey}}"
                textSize = 13f
            }

            val sp = Spinner(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (160 * resources.displayMetrics.density).toInt(),
                    (38 * resources.displayMetrics.density).toInt()
                )
                this.adapter = adapter
                val currentVarCol = mapping.vars[vKey] ?: -1
                val idx = options.indexOfFirst { it.index == currentVarCol }
                if (idx >= 0) setSelection(idx)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        val o = p?.getItemAtPosition(pos) as? ColOption ?: return
                        mapping.vars[vKey] = o.index
                        updatePreview()
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            }

            row.addView(tv)
            row.addView(sp)
            varContainer.addView(row)
        }
    }

    private fun updatePreview() {
        val previewData = dataRows.take(10)
        val previewMapped = mapRows(previewData, mapping, varKeys)

        val totalValid = mapRows(dataRows, mapping, varKeys).count { it.valid }
        val canImport = mapping.phone >= 0 && totalValid > 0

        binding.btnDoImport.isEnabled = canImport
        binding.btnDoImport.text = getString(R.string.import_action_button, totalValid)

        binding.recyclerImportPreview.adapter = PreviewAdapter(previewMapped)
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

    private class PreviewAdapter(
        private val items: List<ContactDraft>
    ) : RecyclerView.Adapter<PreviewAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemImportPreviewRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemImportPreviewRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val b = holder.binding

            b.tvPreviewName.text = item.name.ifBlank { "(Tanpa Nama)" }
            b.tvPreviewPhone.text = item.phone.ifBlank { "(Tanpa Nomor)" }

            if (item.valid) {
                b.tvPreviewStatus.text = b.root.context.getString(R.string.valid_row)
                b.tvPreviewStatus.setBackgroundColor(0xFFD1FAE5.toInt())
                b.tvPreviewStatus.setTextColor(0xFF065F46.toInt())
            } else {
                b.tvPreviewStatus.text = b.root.context.getString(R.string.invalid_row)
                b.tvPreviewStatus.setBackgroundColor(0xFFFEE2E2.toInt())
                b.tvPreviewStatus.setTextColor(0xFF991B1B.toInt())
            }

            val meta = mutableListOf<String>()
            if (item.labels.isNotEmpty()) meta.add(item.labels.joinToString(","))
            if (item.values.isNotEmpty()) {
                val vals = item.values.map { "${it.key}=${it.value}" }.joinToString(" ")
                meta.add(vals)
            }
            if (meta.isNotEmpty()) {
                b.tvPreviewMeta.text = meta.joinToString(" • ")
                b.tvPreviewMeta.visibility = View.VISIBLE
            } else {
                b.tvPreviewMeta.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val TAG = "ContactImportDialog"

        fun newInstance(
            fileName: String,
            headers: List<String>,
            dataRows: List<List<String>>,
            mapping: Mapping,
            templateId: String?,
            varKeys: List<String>,
            onImportConfirmed: (List<Contact>, Int) -> Unit
        ): ContactImportDialog {
            val d = ContactImportDialog()
            d.fileName = fileName
            d.headers = headers
            d.dataRows = dataRows
            d.mapping = mapping
            d.templateId = templateId
            d.varKeys = varKeys
            d.onImportConfirmed = onImportConfirmed
            return d
        }
    }
}
