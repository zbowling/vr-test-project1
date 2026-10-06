package io.github.zbowling.lightdeck.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet6Address

data class DiscoveredServer(val name: String, val address: String)

/** Finds Home Assistant servers through their mDNS advertisement (_home-assistant._tcp). */
class ServerDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    fun servers(): Flow<List<DiscoveredServer>> = callbackFlow {
        val lock = Any()
        val found = LinkedHashMap<String, DiscoveredServer>()
        val toResolve = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        // Before Android 14, NsdManager resolves one service at a time.
        fun resolveNext() {
            if (resolving) return
            val next = toResolve.removeFirstOrNull() ?: return
            resolving = true
            @Suppress("DEPRECATION")
            nsd.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) = synchronized(lock) {
                        resolving = false
                        toServer(info)?.let {
                            found[info.serviceName] = it
                            trySend(found.values.toList())
                        }
                        resolveNext()
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = synchronized(lock) {
                        resolving = false
                        resolveNext()
                    }
                },
            )
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) = synchronized(lock) {
                toResolve.addLast(info)
                resolveNext()
            }

            override fun onServiceLost(info: NsdServiceInfo) = synchronized(lock) {
                if (found.remove(info.serviceName) != null) trySend(found.values.toList())
                Unit
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    private fun toServer(info: NsdServiceInfo): DiscoveredServer? {
        val attributes = info.attributes.mapValues { (_, value) -> value?.decodeToString() }
        val name = attributes["location_name"] ?: info.serviceName
        attributes["internal_url"]?.takeIf { it.isNotBlank() }?.let { return DiscoveredServer(name, it) }
        attributes["base_url"]?.takeIf { it.isNotBlank() }?.let { return DiscoveredServer(name, it) }
        @Suppress("DEPRECATION")
        val host = info.host ?: return null
        val hostText = if (host is Inet6Address) "[${host.hostAddress}]" else host.hostAddress
        return DiscoveredServer(name, "http://$hostText:${info.port}")
    }

    private companion object {
        const val SERVICE_TYPE = "_home-assistant._tcp"
    }
}
