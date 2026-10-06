package io.github.zbowling.lightdeck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.zbowling.lightdeck.ha.ConnectionStatus
import io.github.zbowling.lightdeck.ha.HaSession
import io.github.zbowling.lightdeck.ha.HaUrls
import io.github.zbowling.lightdeck.ha.Light
import io.github.zbowling.lightdeck.ha.LightRepository
import io.github.zbowling.lightdeck.ha.Rgb
import io.github.zbowling.lightdeck.ha.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class LightsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val repository = MutableStateFlow<LightRepository?>(null)
    private var runJob: Job? = null

    private val _config = MutableStateFlow(settings.load())
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    private val _showSetup = MutableStateFlow(_config.value == null)
    val showSetup: StateFlow<Boolean> = _showSetup.asStateFlow()

    val status: StateFlow<ConnectionStatus> = repository
        .flatMapLatest { it?.status ?: flowOf(ConnectionStatus.Connecting) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.Connecting)

    val rooms: StateFlow<List<Room>> = repository
        .flatMapLatest { it?.rooms ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Home Assistant servers found on the local network; only searches while collected. */
    val discoveredServers: StateFlow<List<DiscoveredServer>> = ServerDiscovery(app).servers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        _config.value?.let(::start)
    }

    fun connect(address: String, token: String) {
        try {
            HaUrls.webSocketUrl(address)
        } catch (e: IllegalArgumentException) {
            _messages.tryEmit(e.message ?: "Invalid address")
            return
        }
        if (token.isBlank()) {
            _messages.tryEmit("Paste a long-lived access token")
            return
        }
        val config = ServerConfig(address.trim(), token.trim())
        settings.save(config)
        _config.value = config
        _showSetup.value = false
        start(config)
    }

    fun editServer() {
        _showSetup.value = true
    }

    fun cancelEdit() {
        if (_config.value != null) _showSetup.value = false
    }

    fun retry() {
        _config.value?.let(::start)
    }

    fun toggle(light: Light) = act { it.toggle(light.entityId) }

    fun setBrightness(light: Light, percent: Int) = act { it.setBrightness(light.entityId, percent) }

    fun setColor(light: Light, color: Rgb) = act { it.setColor(light.entityId, color) }

    fun setRoomPower(room: Room, on: Boolean) = act { it.setRoomPower(room, on) }

    private fun start(config: ServerConfig) {
        runJob?.cancel()
        val url = HaUrls.webSocketUrl(config.address)
        val repo = LightRepository(connect = { HaSession.connect(httpClient, url, config.token) })
        repository.value = repo
        runJob = viewModelScope.launch { repo.run() }
    }

    private fun act(block: suspend (LightRepository) -> Unit) {
        val repo = repository.value ?: return
        viewModelScope.launch {
            try {
                block(repo)
            } catch (e: IOException) {
                _messages.emit(e.message ?: "Home Assistant didn't accept that")
            }
        }
    }
}
