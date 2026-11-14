package net.myryzz.wacast.core

import net.myryzz.wacast.model.AppState
import org.json.JSONObject
import java.io.File

/** Where the document came from / whether it was usable. `warning` is null on a clean load. */
data class LoadResult(val state: AppState, val warning: String? = null)

/**
 * File-backed document store. Replaces the web app's `localStorage` with one JSON file in the app's
 * private directory, so the on-disk shape stays interchangeable with `wa-broadcast:v1`.
 *
 * Takes a plain [File] directory, not a Context, which keeps it unit testable.
 */
class StateStore(private val dir: File) {

    private val dataFile = File(dir, DATA_FILE)
    private val corruptFile = File(dir, CORRUPT_FILE)

    fun load(): LoadResult {
        if (!dataFile.exists()) return LoadResult(emptyState())
        val text = try {
            dataFile.readText()
        } catch (e: Exception) {
            return LoadResult(emptyState(), "storage")
        }
        return try {
            val parsed = JSONObject(text)
            if (parsed.optJSONArray("templates") == null || parsed.optJSONArray("contacts") == null) {
                throw IllegalArgumentException("bad shape")
            }
            if (parsed.optInt("schemaVersion", 1) > SCHEMA_VERSION) {
                LoadResult(emptyState(), "newer")
            } else {
                LoadResult(migrate(parsed))
            }
        } catch (e: Exception) {
            // Keep the unreadable payload around so a support request can recover it.
            try {
                corruptFile.writeText(text)
            } catch (_: Exception) {
                // best effort
            }
            LoadResult(emptyState(), "corrupt")
        }
    }

    /** Write the whole document. Returns false when the write failed. */
    fun save(state: AppState): Boolean = try {
        state.updatedAt = nowIso()
        val tmp = File(dir, "$DATA_FILE.tmp")
        tmp.writeText(Json.encode(state))
        if (!tmp.renameTo(dataFile)) {
            dataFile.writeText(tmp.readText())
            tmp.delete()
        }
        true
    } catch (e: Exception) {
        false
    }
}
