package lk.cba.isohostsim.domain.service.remoteHost

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class RemoteConnection {
    data object Idle : RemoteConnection()
    data object Connecting : RemoteConnection()
    data object Live : RemoteConnection()
    data class Retrying(val message: String, val inSeconds: Int) : RemoteConnection()
}

/**
 * One remote host. History comes over HTTP; new traffic arrives over the WebSocket at /api/stream,
 * already filtered by the host to the tester's TID and MID. The stream reconnects on its own and
 * refills history after a reconnect or a "gap" (events the host dropped because we fell behind).
 */
class RemoteHostClient(baseUrl: String) : HostFeed {
    val baseUrl: String = normalizeBaseUrl(baseUrl)
    private val wsUrl = this.baseUrl.replaceFirst("http", "ws") + "/api/stream"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val http = HttpClient(CIO) {
        install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000 }
        install(ContentNegotiation) { json(json) }
        install(WebSockets) { pingIntervalMillis = 20_000 }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null
    @Volatile private var session: DefaultClientWebSocketSession? = null

    private val _connection = MutableStateFlow<RemoteConnection>(RemoteConnection.Idle)
    val connection: StateFlow<RemoteConnection> = _connection.asStateFlow()
    private val _health = MutableStateFlow<RemoteHealth?>(null)
    val health: StateFlow<RemoteHealth?> = _health.asStateFlow()
    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile: StateFlow<RemoteProfile?> = _profile.asStateFlow()
    private val _txns = MutableStateFlow<List<RemoteTxn>>(emptyList())
    override val transactions: StateFlow<List<RemoteTxn>> = _txns.asStateFlow()
    private val _logs = MutableStateFlow<List<RemoteLog>>(emptyList())
    val logs: StateFlow<List<RemoteLog>> = _logs.asStateFlow()
    private val _overrides = MutableStateFlow<List<RemoteOverride>>(emptyList())
    override val overrides: StateFlow<List<RemoteOverride>> = _overrides.asStateFlow()
    private val _olderTxns = MutableStateFlow<String?>(null)
    /** Cursor for the next older page of transactions, or null when everything is loaded. */
    override val olderTransactionsCursor: StateFlow<String?> = _olderTxns.asStateFlow()

    @Volatile var filter: TerminalFilter = TerminalFilter()
        private set

    companion object {
        const val LIVE_TXN_CAP = 2000
        const val LIVE_LOG_CAP = 5000
        private const val PAGE = 200

        /** Checks a URL before it is saved: returns the host's health, or throws with a readable reason. */
        suspend fun probe(url: String): RemoteHealth {
            val client = RemoteHostClient(url)
            try {
                return client.fetchHealth()
            } finally {
                client.close()
            }
        }
    }

    private suspend inline fun <reified T> ok(response: HttpResponse): T {
        if (!response.status.isSuccess()) {
            val text = runCatching { response.bodyAsText() }.getOrDefault("")
            val reason = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            throw IllegalStateException(reason ?: "the host answered ${response.status.value}")
        }
        return response.body()
    }

    suspend fun fetchHealth(): RemoteHealth = ok(http.get("$baseUrl/api/health"))

    /** Starts (or restarts) watching with this filter. */
    fun start(newFilter: TerminalFilter) {
        filter = newFilter
        streamJob?.cancel()
        _txns.value = emptyList()
        _logs.value = emptyList()
        streamJob = scope.launch { runStream() }
    }

    /** Changes the filter on the open stream and reloads history for it. */
    fun changeFilter(newFilter: TerminalFilter) {
        if (newFilter == filter && streamJob?.isActive == true) return
        start(newFilter)
    }

    private suspend fun runStream() {
        var backoff = 1
        while (scope.isActive) {
            _connection.value = RemoteConnection.Connecting
            try {
                _health.value = fetchHealth()
                _profile.value = ok(http.get("$baseUrl/api/profile"))
                refreshOverrides()
                http.webSocket(wsUrl) {
                    session = this
                    send(Frame.Text(subscribeMessage(filter)))
                    // History after subscribing, so nothing that arrives in between is missed;
                    // records seen twice are merged by id.
                    reloadHistory()
                    _connection.value = RemoteConnection.Live
                    backoff = 1
                    for (frame in incoming) {
                        if (frame is Frame.Text) handle(frame.readText())
                    }
                }
                throw IllegalStateException("the host closed the stream")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                session = null
                _connection.value = RemoteConnection.Retrying(e.message ?: e.javaClass.simpleName, backoff)
                delay(backoff * 1000L)
                backoff = (backoff * 2).coerceAtMost(30)
            }
        }
    }

    private fun subscribeMessage(f: TerminalFilter): String = buildJsonObject {
        put("type", "subscribe")
        put("filter", buildJsonObject {
            if (f.tid.isNotBlank()) put("tid", f.tid.trim())
            if (f.mid.isNotBlank()) put("mid", f.mid.trim())
        })
    }.toString()

    private suspend fun handle(text: String) {
        val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (msg["type"]?.jsonPrimitive?.contentOrNull) {
            "txn" -> msg["data"]?.let { d -> addTxns(listOf(json.decodeFromJsonElement(RemoteTxn.serializer(), d))) }
            "log" -> msg["data"]?.let { d -> addLogs(listOf(json.decodeFromJsonElement(RemoteLog.serializer(), d))) }
            "override" -> refreshOverrides()
            "gap" -> reloadHistory()
        }
    }

    private fun addTxns(items: List<RemoteTxn>) = _txns.update { current ->
        (items + current).distinctBy { it.id }.sortedByDescending { it.createdAt }.take(LIVE_TXN_CAP)
    }

    private fun addLogs(items: List<RemoteLog>) = _logs.update { current ->
        (items + current).distinctBy { it.id }.sortedByDescending { it.createdAt }.take(LIVE_LOG_CAP)
    }

    private suspend fun reloadHistory() {
        val f = filter
        val txns: Page<RemoteTxn> = ok(http.get("$baseUrl/api/transactions") { filterParams(f); parameter("limit", PAGE) })
        val logs: Page<RemoteLog> = ok(http.get("$baseUrl/api/logs") { filterParams(f); parameter("limit", 500) })
        if (f != filter) return
        addTxns(txns.items)
        addLogs(logs.items)
        _olderTxns.value = txns.next
    }

    private fun io.ktor.client.request.HttpRequestBuilder.filterParams(f: TerminalFilter) {
        if (f.tid.isNotBlank()) parameter("tid", f.tid.trim())
        if (f.mid.isNotBlank()) parameter("mid", f.mid.trim())
    }

    /** Loads the next older page of transactions for the current filter. */
    override suspend fun loadOlderTransactions() {
        val cursor = _olderTxns.value ?: return
        val f = filter
        val page: Page<RemoteTxn> = ok(http.get("$baseUrl/api/transactions") { filterParams(f); parameter("limit", PAGE); parameter("before", cursor) })
        if (f != filter) return
        _txns.update { current -> (current + page.items).distinctBy { it.id }.sortedByDescending { it.createdAt } }
        _olderTxns.value = page.next
    }

    suspend fun refreshOverrides() {
        val page: Page<RemoteOverride> = ok(http.get("$baseUrl/api/overrides"))
        _overrides.value = page.items
    }

    override suspend fun setOverride(request: OverrideRequest): RemoteOverride {
        val created: RemoteOverride = ok(http.post("$baseUrl/api/overrides") {
            contentType(ContentType.Application.Json)
            setBody(request)
        })
        refreshOverrides()
        return created
    }

    /** Pauses or resumes an override; a paused one stays listed but the terminal gets normal replies. */
    override suspend fun pauseOverride(id: String, paused: Boolean, by: String?): RemoteOverride {
        val updated: RemoteOverride = ok(http.patch("$baseUrl/api/overrides/$id") {
            contentType(ContentType.Application.Json)
            setBody(PauseRequest(paused, by))
        })
        refreshOverrides()
        return updated
    }

    override suspend fun deleteOverride(id: String) {
        val response = http.delete("$baseUrl/api/overrides/$id")
        if (!response.status.isSuccess() && response.status.value != 404) ok<JsonObject>(response)
        refreshOverrides()
    }

    fun close() {
        scope.cancel()
        http.close()
    }
}
