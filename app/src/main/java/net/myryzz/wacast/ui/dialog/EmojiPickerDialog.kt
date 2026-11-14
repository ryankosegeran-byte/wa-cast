package net.myryzz.wacast.ui.dialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import net.myryzz.wacast.AppRepository
import net.myryzz.wacast.R
import net.myryzz.wacast.core.DEFAULT_RECENT_EMOJIS
import net.myryzz.wacast.core.EMOJI_CATEGORIES
import net.myryzz.wacast.core.EmojiItem
import net.myryzz.wacast.core.filterEmojis
import net.myryzz.wacast.core.lookupEmoji
import net.myryzz.wacast.databinding.DialogEmojiPickerBinding
import net.myryzz.wacast.databinding.ItemEmojiCellBinding

class EmojiPickerDialog : DialogFragment() {

    private var _binding: DialogEmojiPickerBinding? = null
    private val binding get() = _binding!!

    var onEmojiPicked: ((String) -> Unit)? = null

    private var currentQuery = ""
    private var selectedCategoryId = "recent"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEmojiPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener { dismiss() }

        binding.recyclerEmojis.layoutManager = GridLayoutManager(requireContext(), 7)

        setupSearch()
        setupCategories()
        renderEmojis()
    }

    private fun setupSearch() {
        binding.etEmojiSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                currentQuery = s?.toString()?.trim() ?: ""
                binding.btnClearEmojiSearch.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                binding.scrollCategories.visibility = if (currentQuery.isNotEmpty()) View.GONE else View.VISIBLE
                renderEmojis()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearEmojiSearch.setOnClickListener {
            binding.etEmojiSearch.setText("")
        }
    }

    private fun setupCategories() {
        val group = binding.chipGroupCategories
        group.removeAllViews()

        // 1. Recent tab
        val recentChip = Chip(requireContext()).apply {
            text = getString(R.string.recent_emojis)
            isCheckable = true
            isChecked = selectedCategoryId == "recent"
            setOnClickListener {
                selectedCategoryId = "recent"
                renderEmojis()
            }
        }
        group.addView(recentChip)

        // 2. Builtin category tabs
        for (cat in EMOJI_CATEGORIES) {
            val chip = Chip(requireContext()).apply {
                text = cat.name
                isCheckable = true
                isChecked = selectedCategoryId == cat.id
                setOnClickListener {
                    selectedCategoryId = cat.id
                    renderEmojis()
                }
            }
            group.addView(chip)
        }
    }

    private fun renderEmojis() {
        val items: List<EmojiItem> = if (currentQuery.isNotEmpty()) {
            filterEmojis(currentQuery)
        } else if (selectedCategoryId == "recent") {
            val recents = AppRepository.getRecentEmojis()
            val finalRecents = if (recents.isNotEmpty()) recents else DEFAULT_RECENT_EMOJIS
            finalRecents.map { lookupEmoji(it) }
        } else {
            EMOJI_CATEGORIES.firstOrNull { it.id == selectedCategoryId }?.emojis ?: emptyList()
        }

        binding.recyclerEmojis.adapter = EmojiAdapter(items) { emoji ->
            AppRepository.pushRecentEmoji(emoji)
            onEmojiPicked?.invoke(emoji)
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

    private class EmojiAdapter(
        private val items: List<EmojiItem>,
        private val onPick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemEmojiCellBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemEmojiCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvEmoji.text = item.char
            holder.itemView.setOnClickListener { onPick(item.char) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val TAG = "EmojiPickerDialog"

        fun newInstance(onEmojiPicked: (String) -> Unit): EmojiPickerDialog {
            val d = EmojiPickerDialog()
            d.onEmojiPicked = onEmojiPicked
            return d
        }
    }
}
