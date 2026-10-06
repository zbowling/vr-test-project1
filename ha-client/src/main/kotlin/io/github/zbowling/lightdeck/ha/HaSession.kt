package io.github.zbowling.lightdeck.ha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Home Assistant rejected the access token. Retrying won't help until the user fixes it. */
class HaAuthException(message: String) : IOException(message)

/** Home Assistant answered a command with an error result. */
class HaCommandException(val code: String, message: String) : IOException("$message ($code)")

/**
 * One authenticated connection to the Home Assistant WebSocket API
 * (https://developers.home-assistant.io/docs/api/websocket).
 * Create it with [connect]; once the connection drops, every call fails and
 * a new session is needed.
 */
class HaSession private constructor(private val commandTimeoutMs: Long) : Closeable {
    private lateinit var webSocket: WebSocket
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement?>>()
    private val subscriptions = ConcurrentHashMap<Int, Channel<JsonObject>>()
    private val authenticated = CompletableDeferred<String?>()
    private val closeCause = AtomicReference<IOException?>()
    private val closed = CompletableDeferred<Unit>()

    /** Version reported by the server during authentication. */
    var haVersion: String? = null
        private set

    /** Sends a command and returns its `result`, or throws [HaCommandException]/[IOException]. */
    suspend fun command(type: String, fields: JsonObjectBuilder.() -> Unit = {}): JsonElement? =
        request(nextId.getAndIncrement(), type, fields)

    /**
     * Subscribes to a Home Assistant event type. The channel receives each event's
     * `event` object and is closed with an [IOException] when the connection drops.
     */
    suspend fun subscribeEvents(eventType: String): ReceiveChannel<JsonObject> {
        val id = nextId.getAndIncrement()
        val channel = Channel<JsonObject>(Channel.UNLIMITED)
        subscriptions[id] = channel
        try {
            request(id, "subscribe_events") { put("event_type", eventType) }
        } catch (e: Exception) {
            subscriptions.remove(id)
            channel.close(e)
            throw e
        }
        return channel
    }

    /** Suspends until the connection ends and returns why. */
    suspend fun awaitClosed(): IOException {
        closed.await()
        return closeCause.get()!!
    }

    override fun close() {
        if (::webSocket.isInitialized) webSocket.close(1000, null)
        shutdown(IOException("Connection closed"))
    }

    private suspend fun request(
        id: Int,
        type: String,
        fields: JsonObjectBuilder.() -> Unit,
    ): JsonElement? {
        val result = CompletableDeferred<JsonElement?>()
        pending[id] = result
        try {
            closeCause.get()?.let { throw it }
            val message = buildJsonObject {
                put("id", id)
                put("type", type)
                fields()
            }
            if (!webSocket.send(message.toString())) throw IOException("Connection closed")
            return withTimeout(commandTimeoutMs) { result.await() }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Home Assistant did not answer \"$type\" in time")
        } finally {
            pending.remove(id)
        }
    }

    private fun shutdown(cause: IOException) {
        if (!closeCause.compareAndSet(null, cause)) return
        authenticated.completeExceptionally(cause)
        pending.values.forEach { it.completeExceptionally(cause) }
        subscriptions.values.forEach { it.close(cause) }
        closed.complete(Unit)
    }

    private fun handle(socket: WebSocket, message: JsonObject, accessToken: String) {
        val id = message.int("id")
        when (message.string("type")) {
            "auth_required" -> socket.send(
                buildJsonObject {
                    put("type", "auth")
                    put("access_token", accessToken)
                }.toString(),
            )
            "auth_ok" -> authenticated.complete(message.string("ha_version"))
            "auth_invalid" -> {
                val error = HaAuthException(message.string("message") ?: "Invalid access token")
                authenticated.completeExceptionally(error)
                socket.close(1000, null)
                shutdown(error)
            }
            "result" -> {
                val result = id?.let(pending::remove) ?: return
                if (message.boolean("success") == true) {
                    result.complete(message["result"])
                } else {
                    val error = message.obj("error")
                    result.completeExceptionally(
                        HaCommandException(
                            code = error?.string("code") ?: "unknown_error",
                            message = error?.string("message") ?: "Command failed",
                        ),
                    )
                }
            }
            "event" -> {
                val event = message.obj("event") ?: return
                id?.let(subscriptions::get)?.trySend(event)
            }
        }
    }

    private inner class Listener(private val accessToken: String) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            // Home Assistant may batch several messages into one JSON array.
            when (val parsed = runCatching { HaJson.parseToJsonElement(text) }.getOrNull()) {
                is JsonObject -> handle(webSocket, parsed, accessToken)
                is JsonArray -> parsed.objects().forEach { handle(webSocket, it, accessToken) }
                else -> Unit
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            shutdown(IOException("Home Assistant closed the connection ($code $reason)".trim()))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            shutdown(IOException("Connection closed"))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val message = when {
                response != null -> "Home Assistant answered HTTP ${response.code}"
                else -> t.message ?: t.javaClass.simpleName
            }
            shutdown(t as? IOException ?: IOException(message, t))
        }
    }

    companion object {
        /**
         * Opens [url] (see [HaUrls.webSocketUrl]) and authenticates with a long-lived
         * access token. Throws [HaAuthException] if the token is rejected.
         */
        suspend fun connect(
            client: OkHttpClient,
            url: String,
            accessToken: String,
            timeoutMs: Long = 15_000,
        ): HaSession {
            val session = HaSession(commandTimeoutMs = timeoutMs)
            val request = Request.Builder().url(url).build()
            session.webSocket = client.newWebSocket(request, session.Listener(accessToken))
            try {
                session.haVersion = withTimeout(timeoutMs) { session.authenticated.await() }
            } catch (e: TimeoutCancellationException) {
                session.close()
                throw IOException("Timed out connecting to $url")
            } catch (e: Throwable) {
                session.close()
                throw e
            }
            return session
        }
    }
}
