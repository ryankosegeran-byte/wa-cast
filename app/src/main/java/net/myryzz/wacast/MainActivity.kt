package net.myryzz.wacast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import net.myryzz.wacast.core.Json
import net.myryzz.wacast.core.mergeStates
import net.myryzz.wacast.core.nowIso
import net.myryzz.wacast.core.validateState
import net.myryzz.wacast.databinding.ActivityMainBinding
import net.myryzz.wacast.ui.compose.ComposeFragment
import net.myryzz.wacast.ui.contacts.ContactsFragment
import net.myryzz.wacast.ui.dialog.ExportDialog
import net.myryzz.wacast.ui.dialog.ImportDialog
import net.myryzz.wacast.ui.preview.PreviewFragment
import net.myryzz.wacast.ui.send.SendFragment
import net.myryzz.wacast.ui.templates.TemplatesFragment
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val noticeDismissHandler = Handler(Looper.getMainLooper())
    private val noticeDismissRunnable = Runnable {
        AppRepository.clearNotice()
    }

    private var activeFragmentTag: String = TAG_TEMPLATES

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            saveBackupToUri(uri)
        }
    }

    private val importDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleImportUri(uri)
        }
    }

    private val repositoryListener = {
        runOnUiThread {
            renderNotice()
            updateSendBadge()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize state repository first
        AppRepository.init(
            filesDir = filesDir,
            sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        )

        // Apply theme before view inflation
        applyAppTheme(AppRepository.theme)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupNoticeBanner()
        setupFragments(savedInstanceState)
        setupBottomNav()

        AppRepository.addListener(repositoryListener)
        renderNotice()
        updateSendBadge()
    }

    override fun onDestroy() {
        super.onDestroy()
        noticeDismissHandler.removeCallbacks(noticeDismissRunnable)
        AppRepository.removeListener(repositoryListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_TAB, activeFragmentTag)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val langItem = menu.findItem(R.id.action_lang)
        if (langItem != null) {
            val currentLang = AppRepository.lang.uppercase()
            langItem.title = getString(R.string.menu_lang) + " [$currentLang]"
        }
        val themeItem = menu.findItem(R.id.action_theme)
        if (themeItem != null) {
            val currentTheme = if (AppRepository.theme == "dark") "Gelap" else "Terang"
            themeItem.title = getString(R.string.menu_theme) + " [$currentTheme]"
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_lang -> {
                val nextLang = if (AppRepository.lang == "id") "en" else "id"
                AppRepository.lang = nextLang
                val label = if (nextLang == "id") "Bahasa Indonesia" else "English"
                AppRepository.postNotice("Bahasa diubah ke $label")
                invalidateOptionsMenu()
                true
            }
            R.id.action_theme -> {
                val nextTheme = if (AppRepository.theme == "dark") "light" else "dark"
                AppRepository.theme = nextTheme
                applyAppTheme(nextTheme)
                invalidateOptionsMenu()
                true
            }
            R.id.action_contacts -> {
                binding.bottomNav.selectedItemId = R.id.nav_contacts
                true
            }
            R.id.action_export -> {
                ExportDialog.newInstance(
                    onSaveFile = { launchSaveBackup() },
                    onShare = { shareBackup() }
                ).show(supportFragmentManager, ExportDialog.TAG)
                true
            }
            R.id.action_import -> {
                importDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                true
            }
            R.id.action_import_contacts,
            R.id.action_help,
            R.id.action_about -> {
                Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchSaveBackup() {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        exportDocumentLauncher.launch("wa-broadcast-backup-$dateStr.json")
    }

    private fun saveBackupToUri(uri: Uri) {
        try {
            val state = AppRepository.state.copy(updatedAt = nowIso())
            val jsonString = Json.encode(state)
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            AppRepository.postNotice(getString(R.string.export_done))
        } catch (_: Exception) {
            AppRepository.postNotice(getString(R.string.export_failed))
        }
    }

    private fun shareBackup() {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val filename = "wa-broadcast-backup-$dateStr.json"
            val exportDir = File(cacheDir, "export").apply { mkdirs() }
            val file = File(exportDir, filename)
            val state = AppRepository.state.copy(updatedAt = nowIso())
            file.writeText(Json.encode(state), Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_share)))
        } catch (_: Exception) {
            AppRepository.postNotice(getString(R.string.export_failed))
        }
    }

    private fun handleImportUri(uri: Uri) {
        val jsonText = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Exception) {
            AppRepository.postNotice(getString(R.string.import_bad_file))
            return
        }

        if (jsonText.isNullOrBlank()) {
            AppRepository.postNotice(getString(R.string.import_bad_file))
            return
        }

        val parsed = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            AppRepository.postNotice(getString(R.string.import_not_json))
            return
        }

        val result = validateState(parsed)
        if (!result.ok || result.data == null || result.counts == null) {
            val errorMsg = when (result.error) {
                "newer" -> getString(R.string.import_newer)
                "phone" -> getString(R.string.import_phone_invalid)
                else -> getString(R.string.import_not_json)
            }
            AppRepository.postNotice(errorMsg)
            return
        }

        val incomingData = result.data
        val counts = result.counts

        ImportDialog.newInstance(
            counts = counts,
            onMerge = {
                mergeStates(AppRepository.state, incomingData)
                AppRepository.saveNow()
                AppRepository.notifyChanged()
                AppRepository.postNotice(getString(R.string.import_done))
            },
            onReplace = {
                AppRepository.setState(incomingData)
                AppRepository.postNotice(getString(R.string.import_done))
            },
            onExportFirst = {
                launchSaveBackup()
            }
        ).show(supportFragmentManager, ImportDialog.TAG)
    }

    private fun setupNoticeBanner() {
        binding.btnDismissNotice.setOnClickListener {
            noticeDismissHandler.removeCallbacks(noticeDismissRunnable)
            AppRepository.clearNotice()
        }
    }

    private fun renderNotice() {
        val message = AppRepository.notice
        if (!message.isNullOrBlank()) {
            binding.tvNotice.text = message
            binding.noticeBanner.visibility = View.VISIBLE
            noticeDismissHandler.removeCallbacks(noticeDismissRunnable)
            noticeDismissHandler.postDelayed(noticeDismissRunnable, 6000)
        } else {
            binding.noticeBanner.visibility = View.GONE
        }
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        val fm = supportFragmentManager
        activeFragmentTag = savedInstanceState?.getString(KEY_ACTIVE_TAB) ?: TAG_TEMPLATES

        var fTemplates = fm.findFragmentByTag(TAG_TEMPLATES)
        var fCompose = fm.findFragmentByTag(TAG_COMPOSE)
        var fPreview = fm.findFragmentByTag(TAG_PREVIEW)
        var fContacts = fm.findFragmentByTag(TAG_CONTACTS)
        var fSend = fm.findFragmentByTag(TAG_SEND)

        val tx = fm.beginTransaction()

        if (fTemplates == null) {
            fTemplates = TemplatesFragment.newInstance()
            tx.add(R.id.fragment_container, fTemplates, TAG_TEMPLATES)
        }
        if (fCompose == null) {
            fCompose = ComposeFragment.newInstance()
            tx.add(R.id.fragment_container, fCompose, TAG_COMPOSE)
        }
        if (fPreview == null) {
            fPreview = PreviewFragment.newInstance()
            tx.add(R.id.fragment_container, fPreview, TAG_PREVIEW)
        }
        if (fContacts == null) {
            fContacts = ContactsFragment.newInstance()
            tx.add(R.id.fragment_container, fContacts, TAG_CONTACTS)
        }
        if (fSend == null) {
            fSend = SendFragment.newInstance()
            tx.add(R.id.fragment_container, fSend, TAG_SEND)
        }

        // Show active, hide others
        val all = listOf(
            TAG_TEMPLATES to fTemplates,
            TAG_COMPOSE to fCompose,
            TAG_PREVIEW to fPreview,
            TAG_CONTACTS to fContacts,
            TAG_SEND to fSend
        )
        for ((tag, frag) in all) {
            if (tag == activeFragmentTag) tx.show(frag) else tx.hide(frag)
        }
        tx.commit()
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = when (activeFragmentTag) {
            TAG_COMPOSE -> R.id.nav_compose
            TAG_PREVIEW -> R.id.nav_preview
            TAG_CONTACTS -> R.id.nav_contacts
            TAG_SEND -> R.id.nav_send
            else -> R.id.nav_templates
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetTag = when (item.itemId) {
                R.id.nav_compose -> TAG_COMPOSE
                R.id.nav_preview -> TAG_PREVIEW
                R.id.nav_contacts -> TAG_CONTACTS
                R.id.nav_send -> TAG_SEND
                else -> TAG_TEMPLATES
            }
            switchTab(targetTag)
            true
        }
    }

    private fun switchTab(targetTag: String) {
        if (targetTag == activeFragmentTag) return
        val fm = supportFragmentManager
        val currentFrag = fm.findFragmentByTag(activeFragmentTag)
        val targetFrag = fm.findFragmentByTag(targetTag)
        if (targetFrag != null) {
            val tx = fm.beginTransaction()
            if (currentFrag != null) tx.hide(currentFrag)
            tx.show(targetFrag)
            tx.commit()
            activeFragmentTag = targetTag
        }
    }

    private fun updateSendBadge() {
        val count = if (AppRepository.state.contacts.isNotEmpty()) AppRepository.state.contacts.size else 0
        val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_send)
        if (count > 0) {
            badge.isVisible = true
            badge.number = count
        } else {
            badge.isVisible = false
            badge.clearNumber()
        }
    }

    private fun applyAppTheme(theme: String) {
        val targetMode = if (theme == "dark") {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    companion object {
        private const val PREFS_NAME = "wa-broadcast"
        private const val KEY_ACTIVE_TAB = "active_tab"

        const val TAG_TEMPLATES = "templates"
        const val TAG_COMPOSE = "compose"
        const val TAG_PREVIEW = "preview"
        const val TAG_CONTACTS = "contacts"
        const val TAG_SEND = "send"
    }
}
