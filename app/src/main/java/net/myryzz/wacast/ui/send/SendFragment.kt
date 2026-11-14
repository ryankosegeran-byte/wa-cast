package net.myryzz.wacast.ui.send

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.Wa
import net.myryzz.wacast.core.filterContacts
import net.myryzz.wacast.core.groupOutline
import net.myryzz.wacast.core.nowIso
import net.myryzz.wacast.core.render
import net.myryzz.wacast.core.syncGroups
import net.myryzz.wacast.databinding.FragmentSendBinding
import net.myryzz.wacast.databinding.ItemSendQueueRowBinding
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Template
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SendFragment : Fragment() {

    private var _binding: FragmentSendBinding? = null
    private val binding get() = _binding!!

    private var isInternalUpdate = false
    private var currentAudienceGroup = ""

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
        _binding = FragmentSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerSendQueue.layoutManager = LinearLayoutManager(requireContext())

        setupTemplateSpinner()
        setupAudienceSpinner()
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

    private data class SpinnerItem(val id: String, val label: String) {
        override fun toString(): String = label
    }

    private fun setupTemplateSpinner() {
        binding.spinnerActiveTemplate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInternalUpdate) return
                val item = parent?.getItemAtPosition(position) as? SpinnerItem ?: return
                if (AppRepository.activeTemplateId != item.id) {
                    AppRepository.activeTemplateId = item.id
                    renderAll()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupAudienceSpinner() {
        binding.spinnerAudienceFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInternalUpdate) return
                val item = parent?.getItemAtPosition(position) as? SpinnerItem ?: return
                if (currentAudienceGroup != item.id) {
                    currentAudienceGroup = item.id
                    renderQueue()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnResetStatus.setOnClickListener { confirmResetSentStatus() }
        binding.btnEmptySendAction.setOnClickListener {
            val state = AppRepository.state
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_nav)
            if (state.templates.isEmpty()) {
                bottomNav?.selectedItemId = R.id.nav_compose
            } else {
                bottomNav?.selectedItemId = R.id.nav_contacts
            }
        }
    }

    private fun confirmResetSentStatus() {
        val tpl = getActiveTemplate() ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reset_sent_confirm)
            .setMessage(getString(R.string.reset_sent_desc))
            .setPositiveButton(R.string.reset_sent_status) { _, _ ->
                val state = AppRepository.state
                val queue = filterContacts(state.contacts, "", currentAudienceGroup)
                for (c in queue) {
                    c.sentAt.remove(tpl.id)
                }
                AppRepository.scheduleSave()
                AppRepository.notifyChanged()
                AppRepository.postNotice("Status pengiriman berhasil direset.")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openWhatsAppForContact(template: Template, contact: Contact) {
        val rendered = render(template.body, contact, template, AppRepository.lang)
        val url = Wa.buildWaUrl(contact.phone, rendered)
        val uri = Uri.parse(url)

        if (url.length > 8000) {
            AppRepository.postNotice(getString(R.string.url_too_long_warning, url.length))
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, uri)
                startActivity(fallback)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Gagal membuka WhatsApp.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        contact.sentAt[template.id] = nowIso()
        AppRepository.scheduleSave()
        AppRepository.notifyChanged()
    }

    private fun renderAll() {
        val tpl = getActiveTemplate()
        val allContacts = AppRepository.state.contacts

        if (tpl == null) {
            binding.cardActiveTemplate.visibility = View.GONE
            binding.cardAudienceProgress.visibility = View.GONE
            binding.recyclerSendQueue.visibility = View.GONE
            binding.sendEmptyState.visibility = View.VISIBLE
            binding.tvEmptySendTitle.text = getString(R.string.no_template_preview)
            binding.btnEmptySendAction.text = getString(R.string.new_template)
            return
        }

        if (allContacts.isEmpty()) {
            binding.cardActiveTemplate.visibility = View.GONE
            binding.cardAudienceProgress.visibility = View.GONE
            binding.recyclerSendQueue.visibility = View.GONE
            binding.sendEmptyState.visibility = View.VISIBLE
            binding.tvEmptySendTitle.text = getString(R.string.no_contacts_queue)
            binding.btnEmptySendAction.text = getString(R.string.new_contact)
            return
        }

        binding.sendEmptyState.visibility = View.GONE
        binding.cardActiveTemplate.visibility = View.VISIBLE
        binding.cardAudienceProgress.visibility = View.VISIBLE
        binding.recyclerSendQueue.visibility = View.VISIBLE

        isInternalUpdate = true

        // 1. Active Template Summary
        binding.tvActiveTemplateName.text = tpl.name.ifBlank { getString(R.string.untitled) }
        binding.tvActiveTemplateSnippet.text = tpl.body.replace("\n", " ").trim()

        val templates = AppRepository.state.templates
        val tplItems = templates.map {
            val title = it.name.ifBlank { getString(R.string.untitled) }
            SpinnerItem(it.id, title)
        }
        val tplAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tplItems)
        binding.spinnerActiveTemplate.adapter = tplAdapter
        val currentTplIdx = tplItems.indexOfFirst { it.id == tpl.id }
        if (currentTplIdx >= 0) binding.spinnerActiveTemplate.setSelection(currentTplIdx)

        // 2. Audience Spinner
        val state = AppRepository.state
        syncGroups(state.groups, state.contacts)
        val outline = groupOutline(state.groups)
        val audItems = mutableListOf<SpinnerItem>()
        audItems.add(SpinnerItem("", getString(R.string.all_labels)))
        for (g in outline) {
            val count = state.contacts.count { it.labels.contains(g.name) }
            val prefix = "— ".repeat(g.depth)
            audItems.add(SpinnerItem(g.name, "$prefix${g.name} ($count)"))
        }
        val ungroupedCount = state.contacts.count { it.labels.isEmpty() }
        audItems.add(SpinnerItem("__none__", "${getString(R.string.no_label)} ($ungroupedCount)"))

        val audAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, audItems)
        binding.spinnerAudienceFilter.adapter = audAdapter
        val currentAudIdx = audItems.indexOfFirst { it.id == currentAudienceGroup }
        if (currentAudIdx >= 0) binding.spinnerAudienceFilter.setSelection(currentAudIdx)

        isInternalUpdate = false

        renderQueue()
    }

    private fun renderQueue() {
        val tpl = getActiveTemplate() ?: return
        val queue = filterContacts(AppRepository.state.contacts, "", currentAudienceGroup)

        val total = queue.size
        val sentCount = queue.count { it.sentAt.containsKey(tpl.id) }
        val pct = if (total > 0) (sentCount * 100) / total else 0

        binding.progressSend.progress = pct
        binding.tvProgressText.text = getString(R.string.progress_ratio, sentCount, total, pct)

        val nextContact = queue.firstOrNull { !it.sentAt.containsKey(tpl.id) }
        if (nextContact != null) {
            val targetName = nextContact.name.ifBlank { nextContact.phone }
            binding.btnSendNext.isEnabled = true
            binding.btnSendNext.text = getString(R.string.send_next, targetName)
            binding.btnSendNext.setOnClickListener { openWhatsAppForContact(tpl, nextContact) }
        } else {
            binding.btnSendNext.isEnabled = false
            binding.btnSendNext.text = getString(R.string.all_sent)
            binding.btnSendNext.setOnClickListener(null)
        }

        binding.recyclerSendQueue.adapter = SendQueueAdapter(
            contacts = queue,
            templateId = tpl.id,
            onSendContact = { c -> openWhatsAppForContact(tpl, c) }
        )
    }

    private class SendQueueAdapter(
        private val contacts: List<Contact>,
        private val templateId: String,
        private val onSendContact: (Contact) -> Unit
    ) : RecyclerView.Adapter<SendQueueAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemSendQueueRowBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSendQueueRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val c = contacts[position]
            val b = holder.binding

            val displayName = c.name.ifBlank { c.phone }
            b.tvName.text = displayName
            b.tvPhone.text = c.phone
            b.tvAvatar.text = displayName.take(1).uppercase()

            val sentTimestamp = c.sentAt[templateId]
            val isSent = sentTimestamp != null

            if (isSent) {
                b.tvStatusBadge.text = b.root.context.getString(R.string.status_sent)
                b.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(b.root.context, android.R.color.holo_green_light))
                b.tvStatusBadge.setTextColor(ContextCompat.getColor(b.root.context, android.R.color.black))

                val formattedTime = formatTimestamp(sentTimestamp)
                b.tvSentTime.text = formattedTime
                b.tvSentTime.visibility = View.VISIBLE

                b.btnSendContact.text = b.root.context.getString(R.string.resend)
            } else {
                b.tvStatusBadge.text = b.root.context.getString(R.string.status_pending)
                b.tvStatusBadge.setBackgroundColor(0xFFE5E7EB.toInt())
                b.tvStatusBadge.setTextColor(0xFF374151.toInt())
                b.tvSentTime.visibility = View.GONE

                b.btnSendContact.text = b.root.context.getString(R.string.send_action)
            }

            b.btnSendContact.setOnClickListener { onSendContact(c) }
        }

        private fun formatTimestamp(isoString: String): String {
            return try {
                val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = inputFmt.parse(isoString) ?: return isoString
                val outFmt = SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault())
                outFmt.format(d)
            } catch (_: Exception) {
                isoString.take(16).replace("T", " ")
            }
        }

        override fun getItemCount(): Int = contacts.size
    }

    companion object {
        fun newInstance() = SendFragment()
    }
}
