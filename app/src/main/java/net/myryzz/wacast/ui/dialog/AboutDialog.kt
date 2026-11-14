package net.myryzz.wacast.ui.dialog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import net.myryzz.wacast.R
import net.myryzz.wacast.databinding.DialogAboutBinding

class AboutDialog : DialogFragment() {

    private var _binding: DialogAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val features = listOf(
            R.string.about_feature_1,
            R.string.about_feature_2,
            R.string.about_feature_3,
            R.string.about_feature_4,
            R.string.about_feature_5,
            R.string.about_feature_6,
            R.string.about_feature_7
        ).joinToString("\n\n") { "• " + getString(it) }
        binding.tvAboutFeatures.text = features

        binding.btnCloseAbout.setOnClickListener { dismiss() }
        binding.btnCloseAboutAction.setOnClickListener { dismiss() }

        binding.btnAboutGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ryankosegeran-byte/wa-cast"))
            startActivity(intent)
        }

        binding.btnAboutPrivacy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ryankosegeran-byte.github.io/wa-cast/privacy-policy.html"))
            startActivity(intent)
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
        const val TAG = "AboutDialog"
        fun newInstance() = AboutDialog()
    }
}
