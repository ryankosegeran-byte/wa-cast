package net.myryzz.wacast.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import net.myryzz.wacast.R
import net.myryzz.wacast.core.StateCounts
import net.myryzz.wacast.databinding.DialogImportBinding

class ImportDialog : DialogFragment() {

    private var _binding: DialogImportBinding? = null
    private val binding get() = _binding!!

    var counts: StateCounts? = null
    var onMerge: (() -> Unit)? = null
    var onReplace: (() -> Unit)? = null
    var onExportFirst: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseImport.setOnClickListener { dismiss() }
        binding.btnImportCancel.setOnClickListener { dismiss() }

        val c = counts
        if (c != null) {
            binding.tvImportInfo.text = getString(
                R.string.import_found_info,
                c.templates,
                c.contacts,
                c.labels
            )
        }

        binding.btnImportMerge.setOnClickListener {
            dismiss()
            onMerge?.invoke()
        }

        binding.btnImportReplace.setOnClickListener {
            dismiss()
            onReplace?.invoke()
        }

        binding.btnExportFirst.setOnClickListener {
            onExportFirst?.invoke()
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
        const val TAG = "ImportDialog"

        fun newInstance(
            counts: StateCounts,
            onMerge: () -> Unit,
            onReplace: () -> Unit,
            onExportFirst: () -> Unit
        ): ImportDialog {
            val d = ImportDialog()
            d.counts = counts
            d.onMerge = onMerge
            d.onReplace = onReplace
            d.onExportFirst = onExportFirst
            return d
        }
    }
}
