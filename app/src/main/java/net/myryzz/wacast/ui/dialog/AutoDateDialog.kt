package net.myryzz.wacast.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.AUTO_PRESETS
import net.myryzz.wacast.core.formatAutoDate
import net.myryzz.wacast.databinding.DialogAutoDateBinding
import net.myryzz.wacast.databinding.ItemAutoDateOptionBinding
import java.util.Date

class AutoDateDialog : DialogFragment() {

    private var _binding: DialogAutoDateBinding? = null
    private val binding get() = _binding!!

    var varKey: String = ""
    var currentPreset: String? = null
    var onSelectPreset: ((String?) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAutoDateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvVarSubtitle.text = "{{$varKey}} · " + getString(R.string.auto_date_desc)
        binding.btnClose.setOnClickListener { dismiss() }

        binding.recyclerOptions.layoutManager = LinearLayoutManager(requireContext())

        val lang = AppRepository.lang
        val now = Date()

        val options = mutableListOf<OptionItem>()
        // None option
        options.add(
            OptionItem(
                preset = null,
                label = getString(R.string.auto_none),
                desc = getString(R.string.auto_none_hint),
                preview = "-"
            )
        )

        for (preset in AUTO_PRESETS) {
            val labelRes = when (preset) {
                "dateMonthYear" -> R.string.auto_preset_dateMonthYear
                "monthName" -> R.string.auto_preset_monthName
                "year" -> R.string.auto_preset_year
                "monthNum" -> R.string.auto_preset_monthNum
                "monthYear" -> R.string.auto_preset_monthYear
                "dayName" -> R.string.auto_preset_dayName
                "numericDate" -> R.string.auto_preset_numericDate
                else -> R.string.auto_none
            }
            options.add(
                OptionItem(
                    preset = preset,
                    label = getString(labelRes),
                    desc = "",
                    preview = formatAutoDate(preset, now, lang)
                )
            )
        }

        binding.recyclerOptions.adapter = OptionsAdapter(options, currentPreset) { selected ->
            onSelectPreset?.invoke(selected)
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

    private data class OptionItem(
        val preset: String?,
        val label: String,
        val desc: String,
        val preview: String
    )

    private class OptionsAdapter(
        private val items: List<OptionItem>,
        private val currentPreset: String?,
        private val onPick: (String?) -> Unit
    ) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemAutoDateOptionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAutoDateOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isChecked = (item.preset == null && currentPreset.isNullOrEmpty()) || (item.preset == currentPreset)

            holder.binding.radioBtn.isChecked = isChecked
            holder.binding.tvOptionLabel.text = item.label
            if (item.desc.isNotEmpty()) {
                holder.binding.tvOptionDesc.text = item.desc
                holder.binding.tvOptionDesc.visibility = View.VISIBLE
            } else {
                holder.binding.tvOptionDesc.visibility = View.GONE
            }
            holder.binding.tvOptionPreview.text = item.preview

            holder.itemView.setOnClickListener {
                onPick(item.preset)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val TAG = "AutoDateDialog"

        fun newInstance(
            varKey: String,
            currentPreset: String?,
            onSelectPreset: (String?) -> Unit
        ): AutoDateDialog {
            val d = AutoDateDialog()
            d.varKey = varKey
            d.currentPreset = currentPreset
            d.onSelectPreset = onSelectPreset
            return d
        }
    }
}
