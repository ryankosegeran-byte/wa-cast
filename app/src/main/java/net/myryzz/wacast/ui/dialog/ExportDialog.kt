package net.myryzz.wacast.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import net.myryzz.wacast.databinding.DialogExportBinding

class ExportDialog : DialogFragment() {

    private var _binding: DialogExportBinding? = null
    private val binding get() = _binding!!

    var onSaveFile: (() -> Unit)? = null
    var onShare: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseExport.setOnClickListener { dismiss() }
        binding.btnExportCancel.setOnClickListener { dismiss() }

        binding.btnExportSaveFile.setOnClickListener {
            dismiss()
            onSaveFile?.invoke()
        }

        binding.btnExportShare.setOnClickListener {
            dismiss()
            onShare?.invoke()
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
        const val TAG = "ExportDialog"

        fun newInstance(onSaveFile: () -> Unit, onShare: () -> Unit): ExportDialog {
            val d = ExportDialog()
            d.onSaveFile = onSaveFile
            d.onShare = onShare
            return d
        }
    }
}
