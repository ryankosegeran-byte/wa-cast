package net.myryzz.wacast.core

import net.myryzz.wacast.model.AppState
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StateStoreTest {

    private fun tempDir(): File = Files.createTempDirectory("wa-broadcast-test").toFile()

    @Test
    fun loadReturnsEmptyStateWhenNothingOnDisk() {
        val store = StateStore(tempDir())
        val result = store.load()
        assertNull(result.warning)
        assertTrue(result.state.templates.isEmpty())
    }

    @Test
    fun saveThenLoadRoundTrips() {
        val dir = tempDir()
        val state = AppState(
            schemaVersion = SCHEMA_VERSION,
            templates = mutableListOf(Template(id = "t1", name = "Pengingat", body = "Halo {{nama}}")),
            contacts = mutableListOf(Contact(id = "c1", phone = "6289698129722", name = "FENDY")),
        )
        assertTrue(StateStore(dir).save(state))

        val result = StateStore(dir).load()
        assertNull(result.warning)
        assertEquals("Pengingat", result.state.templates.single().name)
        assertEquals("6289698129722", result.state.contacts.single().phone)
    }

    @Test
    fun corruptPayloadIsBackedUpAndReported() {
        val dir = tempDir()
        File(dir, DATA_FILE).writeText("{not json")
        val result = StateStore(dir).load()
        assertEquals("corrupt", result.warning)
        assertTrue(File(dir, CORRUPT_FILE).exists())
    }

    @Test
    fun newerSchemaIsRefusedWithoutOverwriting() {
        val dir = tempDir()
        File(dir, DATA_FILE).writeText("""{"schemaVersion":99,"templates":[],"contacts":[]}""")
        val result = StateStore(dir).load()
        assertEquals("newer", result.warning)
        assertTrue(result.state.templates.isEmpty())
        // Original payload is left untouched.
        assertTrue(File(dir, DATA_FILE).readText().contains("99"))
    }
}
