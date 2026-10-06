package io.github.zbowling.lightdeck.ha

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HaSessionTest {
    private val ha = FakeHomeAssistant().start()

    @After fun tearDown() = ha.close()

    @Test fun authenticatesAndRunsCommands() = runBlocking {
        ha.acceptConnections()
        ha.states = listOf(FakeHomeAssistant.lightState("light.desk"))
        HaSession.connect(ha.client, ha.url, FakeHomeAssistant.TOKEN).use { session ->
            assertEquals("2026.10.0", session.haVersion)
            val states = session.command("get_states") as JsonArray
            assertEquals(1, states.size)
        }
    }

    @Test fun rejectedTokenThrowsAuthException() {
        ha.acceptConnections()
        assertThrows(HaAuthException::class.java) {
            runBlocking { HaSession.connect(ha.client, ha.url, "wrong") }
        }
    }

    @Test fun errorResultThrowsCommandException() = runBlocking {
        ha.acceptConnections()
        ha.failingCommands = setOf("get_states")
        HaSession.connect(ha.client, ha.url, FakeHomeAssistant.TOKEN).use { session ->
            val error = runCatching { session.command("get_states") }.exceptionOrNull()
            assertTrue(error is HaCommandException)
            assertEquals("unauthorized", (error as HaCommandException).code)
        }
    }

    @Test fun droppedConnectionFailsSubscriptionsAndCommands() = runBlocking {
        ha.acceptConnections()
        HaSession.connect(ha.client, ha.url, FakeHomeAssistant.TOKEN).use { session ->
            val events = session.subscribeEvents("state_changed")
            ha.dropConnection()
            withTimeout(5_000) { session.awaitClosed() }
            assertTrue(runCatching { events.receive() }.exceptionOrNull() is IOException)
            assertTrue(runCatching { session.command("get_states") }.exceptionOrNull() is IOException)
        }
    }
}
