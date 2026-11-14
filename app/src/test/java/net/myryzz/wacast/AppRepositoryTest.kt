package net.myryzz.wacast

import net.myryzz.wacast.model.AppState
import net.myryzz.wacast.model.Contact
import net.myryzz.wacast.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File

    @Before
    fun setup() {
        dir = tmp.newFolder()
        AppRepository.init(dir)
    }

    @Test
    fun testInitEmptyState() {
        assertNotNull(AppRepository.state)
        assertEquals(5, AppRepository.state.schemaVersion)
        assertTrue(AppRepository.state.templates.isEmpty())
        assertTrue(AppRepository.state.contacts.isEmpty())
        assertNull(AppRepository.activeTemplateId)
    }

    @Test
    fun testNoticeLifecycle() {
        var notified = 0
        val listener = {
            notified++
            Unit
        }
        AppRepository.addListener(listener)

        AppRepository.postNotice("Test notice")
        assertEquals("Test notice", AppRepository.notice)
        assertTrue(notified > 0)

        AppRepository.clearNotice()
        assertNull(AppRepository.notice)

        AppRepository.removeListener(listener)
    }

    @Test
    fun testSetStateAndPersistence() {
        val tpl = Template(
            id = "tpl_123",
            name = "Halo",
            body = "Halo {{nama}}",
            createdAt = "2026-11-14T00:00:00.000Z",
            updatedAt = "2026-11-14T00:00:00.000Z"
        )
        val c = Contact(
            id = "c_123",
            phone = "6289612345678",
            name = "Budi"
        )
        val newState = AppState(
            schemaVersion = 5,
            updatedAt = "2026-11-14T00:00:00.000Z",
            templates = mutableListOf(tpl),
            contacts = mutableListOf(c)
        )

        AppRepository.setState(newState, persistImmediately = true)
        assertEquals("tpl_123", AppRepository.activeTemplateId)
        assertEquals("c_123", AppRepository.previewContactId)

        // Reload fresh from disk to prove persistence
        val reloaded = AppRepository.store.load()
        assertEquals(1, reloaded.state.templates.size)
        assertEquals("Halo", reloaded.state.templates[0].name)
        assertEquals(1, reloaded.state.contacts.size)
        assertEquals("6289612345678", reloaded.state.contacts[0].phone)
    }
}
