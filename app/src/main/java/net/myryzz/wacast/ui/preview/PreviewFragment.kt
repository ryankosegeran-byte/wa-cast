package net.myryzz.wacast.ui.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.Wa
import net.myryzz.wacast.core.nowIso
import net.myryzz.wacast.core.render
import net.myryzz.wacast.core.unresolvedVariables
import net.myryzz.wacast.databinding.FragmentPreviewBinding
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Template

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private var isInternalUpdate = false

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
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecipientSpinner()
        setupButtons()

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

    private fun getActiveContact(): Contact? {
        val state = AppRepository.state
        val activeContactId = AppRepository.previewContactId
        var c = state.contacts.firstOrNull { it.id == activeContactId }
        if (c == null && state.contacts.isNotEmpty()) {
            c = state.contacts.first()
            AppRepository.previewContactId = c.id
        }
        return c
    }

    private data class RecipientOption(val id: String?, val label: String) {
        override fun toString(): String = label
    }

    private fun setupRecipientSpinner() {
        binding.spinnerRecipient.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInternalUpdate) return
                val item = parent?.getItemAtPosition(position) as? RecipientOption ?: return
                if (AppRepository.previewContactId != item.id) {
                    AppRepository.previewContactId = item.id
                    renderPreviewContent()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnCopyMessage.setOnClickListener { copyRenderedMessage() }
        binding.btnSendWhatsapp.setOnClickListener { openWhatsAppForRecipient() }
        binding.btnEditTemplate.setOnClickListener { navigateToComposeTab() }
        binding.btnEmptyAction.setOnClickListener { navigateToComposeTab() }
    }

    private fun navigateToComposeTab() {
        activity?.findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_compose
    }

    private fun copyRenderedMessage() {
        val tpl = getActiveTemplate() ?: return
        val contact = getActiveContact()
        val rendered = render(tpl.body, contact, tpl, AppRepository.lang)
        if (rendered.isEmpty()) return

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WhatsApp Cast", rendered)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
    }

    private fun openWhatsAppForRecipient() {
        val tpl = getActiveTemplate() ?: return
        val contact = getActiveContact() ?: return
        if (!Wa.isValidPhone(contact.phone)) return

        val rendered = render(tpl.body, contact, tpl, AppRepository.lang)
        val url = Wa.buildWaUrl(contact.phone, rendered)
        val uri = Uri.parse(url)

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Fallback: system browser / any handler
            try {
                val fallback = Intent(Intent.ACTION_VIEW, uri)
                startActivity(fallback)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Tidak dapat membuka WhatsApp.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Record sent timestamp
        contact.sentAt[tpl.id] = nowIso()
        AppRepository.scheduleSave()
        AppRepository.notifyChanged()
    }

    private fun renderAll() {
        val tpl = getActiveTemplate()
        if (tpl == null) {
            binding.recipientPickerContainer.visibility = View.GONE
            binding.unresolvedWarningBanner.visibility = View.GONE
            binding.previewScrollView.visibility = View.GONE
            binding.previewEmptyState.visibility = View.VISIBLE
            binding.tvEmptyPreviewTitle.text = getString(R.string.no_template_preview)
            return
        }

        binding.previewEmptyState.visibility = View.GONE
        binding.previewScrollView.visibility = View.VISIBLE
        binding.recipientPickerContainer.visibility = View.VISIBLE

        // 1. Setup Recipient Spinner
        val contacts = AppRepository.state.contacts
        val options = mutableListOf<RecipientOption>()
        if (contacts.isEmpty()) {
            options.add(RecipientOption(null, getString(R.string.all_groups)))
        } else {
            for (c in contacts) {
                val label = if (c.name.isNotBlank()) "${c.name} (${c.phone})" else c.phone
                options.add(RecipientOption(c.id, label))
            }
        }

        isInternalUpdate = true
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerRecipient.adapter = adapter
        val activeContact = getActiveContact()
        val currentIdx = options.indexOfFirst { it.id == activeContact?.id }
        if (currentIdx >= 0) binding.spinnerRecipient.setSelection(currentIdx)
        isInternalUpdate = false

        renderPreviewContent()
    }

    private fun renderPreviewContent() {
        val tpl = getActiveTemplate() ?: return
        val contact = getActiveContact()

        // Recipient Meta Header
        if (contact != null) {
            val displayName = contact.name.ifBlank { contact.phone }
            binding.tvRecipientName.text = displayName
            binding.tvRecipientPhone.text = contact.phone
            binding.tvAvatarChar.text = displayName.take(1).uppercase()
            binding.btnSendWhatsapp.isEnabled = Wa.isValidPhone(contact.phone)
        } else {
            binding.tvRecipientName.text = "WhatsApp"
            binding.tvRecipientPhone.text = getString(R.string.no_contacts_preview)
            binding.tvAvatarChar.text = "W"
            binding.btnSendWhatsapp.isEnabled = false
        }

        // Rendered Message
        val rendered = render(tpl.body, contact, tpl, AppRepository.lang)
        if (rendered.isNotBlank()) {
            binding.tvBubbleMessage.text = WaSpannable.format(rendered)
            binding.tvCharCount.text = getString(R.string.chars_count, rendered.length)
            binding.btnCopyMessage.isEnabled = true
        } else {
            binding.tvBubbleMessage.text = getString(R.string.no_message_body)
            binding.tvCharCount.text = getString(R.string.chars_count, 0)
            binding.btnCopyMessage.isEnabled = false
        }

        // Unresolved Variables Warning
        val unresolved = unresolvedVariables(tpl.body, contact, tpl, AppRepository.lang)
        if (unresolved.isNotEmpty()) {
            binding.unresolvedWarningBanner.visibility = View.VISIBLE
            val formattedTokens = unresolved.joinToString(", ") { "{{$it}}" }
            binding.tvUnresolvedText.text = getString(R.string.unresolved_vars_warning, formattedTokens)
        } else {
            binding.unresolvedWarningBanner.visibility = View.GONE
        }
    }

    companion object {
        fun newInstance() = PreviewFragment()
    }
}
