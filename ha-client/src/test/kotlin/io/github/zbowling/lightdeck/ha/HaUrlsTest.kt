package io.github.zbowling.lightdeck.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HaUrlsTest {
    @Test fun bareHostGetsDefaultPort() =
        assertEquals("ws://homeassistant.local:8123/api/websocket", HaUrls.webSocketUrl("homeassistant.local"))

    @Test fun hostWithPort() =
        assertEquals("ws://192.168.1.5:8124/api/websocket", HaUrls.webSocketUrl(" 192.168.1.5:8124 "))

    @Test fun httpsBecomesWss() =
        assertEquals("wss://abc.ui.nabu.casa/api/websocket", HaUrls.webSocketUrl("https://abc.ui.nabu.casa/"))

    @Test fun httpKeepsGivenPort() =
        assertEquals("ws://ha.lan:8123/api/websocket", HaUrls.webSocketUrl("http://ha.lan:8123"))

    @Test fun alreadyAWebSocketUrl() =
        assertEquals("wss://ha.example.com/api/websocket", HaUrls.webSocketUrl("wss://ha.example.com/api/websocket"))

    @Test fun rejectsOtherSchemes() {
        assertThrows(IllegalArgumentException::class.java) { HaUrls.webSocketUrl("ftp://ha.lan") }
    }

    @Test fun rejectsEmpty() {
        assertThrows(IllegalArgumentException::class.java) { HaUrls.webSocketUrl("  ") }
    }
}
