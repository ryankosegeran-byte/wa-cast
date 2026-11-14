package net.myryzz.wacast

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import net.myryzz.wacast.core.StateStore
import net.myryzz.wacast.model.AppState
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single source of truth for app runtime state.
 * Bridges persistence (StateStore) and UI fragments via listener callbacks.
 */
object AppRepository {

    private const val PREF_LANG = "wa-broadcast:lang"
    private const val PREF_THEME = "wa-broadcast:theme"
    private const val PREF_RECENT_EMOJIS = "wa-broadcast:recent-emojis"
    private const val MAX_RECENT_EMOJIS = 16

    lateinit var state: AppState
        private set
    lateinit var store: StateStore
        private set

    var activeTemplateId: String? = null
    var previewContactId: String? = null

    var notice: String? = null
        private set

    private var prefs: SharedPreferences? = null
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    private var saveHandler: Handler? = null
    private val saveRunnable = Runnable { saveNow() }

    fun init(filesDir: File, sharedPrefs: SharedPreferences? = null) {
        prefs = sharedPrefs
        store = StateStore(filesDir)
        val loaded = store.load()
        state = loaded.state
        activeTemplateId = state.templates.firstOrNull()?.id
        previewContactId = state.contacts.firstOrNull()?.id

        // Initialize UI thread handler if Looper is prepared (Android runtime)
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) saveHandler = Handler(looper)
        } catch (_: Throwable) {
            saveHandler = null
        }

        loaded.warning?.let {
            notice = when (it) {
                "newer" -> "Data lebih baru dari versi aplikasi ini."
                "corrupt" -> "Data rusak, salinan tersimpan di .corrupt."
                else -> "Gagal mengakses penyimpanan lokal."
            }
        }
        notifyChanged()
    }

    /**
     * Replace state in-memory (e.g., after full backup replace or merge).
     */
    fun setState(newState: AppState, persistImmediately: Boolean = true) {
        state = newState
        if (state.templates.none { it.id == activeTemplateId }) {
            activeTemplateId = state.templates.firstOrNull()?.id
        }
        if (state.contacts.none { it.id == previewContactId }) {
            previewContactId = state.contacts.firstOrNull()?.id
        }
        if (persistImmediately) saveNow() else scheduleSave()
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        for (listener in listeners) {
            try {
                listener.invoke()
            } catch (_: Throwable) {
                // Keep iterating if one listener fails
            }
        }
    }

    fun postNotice(message: String) {
        notice = message
        notifyChanged()
    }

    fun clearNotice() {
        notice = null
        notifyChanged()
    }

    fun scheduleSave() {
        val h = saveHandler
        if (h != null) {
            h.removeCallbacks(saveRunnable)
            h.postDelayed(saveRunnable, 200)
        } else {
            saveNow()
        }
    }

    fun saveNow() {
        try {
            store.save(state)
        } catch (t: Throwable) {
            postNotice("Penyimpanan lokal penuh atau gagal ditulis.")
        }
    }

    var lang: String
        get() = prefs?.getString(PREF_LANG, "en") ?: "en"
        set(value) {
            prefs?.edit()?.putString(PREF_LANG, value)?.apply()
            notifyChanged()
        }

    var theme: String
        get() = prefs?.getString(PREF_THEME, "light") ?: "light"
        set(value) {
            prefs?.edit()?.putString(PREF_THEME, value)?.apply()
            notifyChanged()
        }

    fun getRecentEmojis(): List<String> {
        val raw = prefs?.getString(PREF_RECENT_EMOJIS, null) ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun pushRecentEmoji(emoji: String) {
        val list = getRecentEmojis().toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        while (list.size > MAX_RECENT_EMOJIS) list.removeAt(list.size - 1)
        prefs?.edit()?.putString(PREF_RECENT_EMOJIS, list.joinToString(","))?.apply()
    }
}
