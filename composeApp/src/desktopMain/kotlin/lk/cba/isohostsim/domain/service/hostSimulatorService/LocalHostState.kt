package lk.cba.isohostsim.domain.service.hostSimulatorService

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.domain.service.remoteHost.HostFeed
import lk.cba.isohostsim.domain.service.remoteHost.OverrideRequest
import lk.cba.isohostsim.domain.service.remoteHost.RemoteField
import lk.cba.isohostsim.domain.service.remoteHost.RemoteMessage
import lk.cba.isohostsim.domain.service.remoteHost.RemoteOverride
import lk.cba.isohostsim.domain.service.remoteHost.RemoteTxn
import lk.cba.isohostsim.domain.service.remoteHost.TerminalFilter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The TID of an override that applies to every terminal. */
const val ALL_TERMINALS = "*"

/** The F39 a settlement gets while a settlement reject is on. */
const val SETTLEMENT_REJECT_CODE = "95"

/**
 * What the Local Host screen shows and controls for one gateway: its recent transactions and its
 * overrides. Kept by gateway id, like [ResponseControl], so it outlives the screen's objects.
 *
 * Overrides use the remote host's model, so both screens share one Overrides tab. On this PC an
 * override can apply to every terminal (TID "*"), and one without an expiry lasts until the host
 * stops.
 */
class LocalHostState {
    private val _transactions = MutableStateFlow<List<RemoteTxn>>(emptyList())
    val transactions: StateFlow<List<RemoteTxn>> = _transactions.asStateFlow()

    private val _overrides = MutableStateFlow<List<RemoteOverride>>(emptyList())
    val overrides: StateFlow<List<RemoteOverride>> = _overrides.asStateFlow()

    fun record(t: RemoteTxn) = _transactions.update { (listOf(t) + it).take(MAX_TRANSACTIONS) }

    fun clearTransactions() {
        _transactions.value = emptyList()
    }

    fun setOverride(r: OverrideRequest): RemoteOverride {
        val kind = r.kind ?: "general"
        val tid = r.tid.trim().ifBlank { ALL_TERMINALS }
        if (kind == "rejectSettlement") {
            require(r.responseCode == null && r.delaySeconds == null && r.noResponse != true) {
                "a settlement reject always answers 95 and takes no response code, delay or no-response"
            }
        } else {
            require(r.responseCode != null || (r.delaySeconds ?: 0) > 0 || r.noResponse == true) {
                "an override needs a response code, a delay or no response"
            }
            require(!(r.noResponse == true && r.responseCode != null)) { "no response cannot be combined with a response code" }
            r.responseCode?.let { require(it.length == 2) { "a response code has 2 characters" } }
        }
        val now = Instant.now()
        val o = RemoteOverride(
            id = UUID.randomUUID().toString(),
            tid = tid,
            kind = kind,
            responseCode = r.responseCode,
            delaySeconds = r.delaySeconds?.takeIf { it > 0 },
            noResponse = r.noResponse?.takeIf { it },
            setBy = r.setBy,
            note = r.note,
            createdAt = now.toString(),
            expiresAt = r.minutes?.takeIf { it > 0 }?.let { now.plusSeconds(it * 60L).toString() },
        )
        _overrides.update { list -> sorted(list.filterNot { it.kind == kind && it.tid == tid } + o) }
        return o
    }

    fun pauseOverride(id: String, paused: Boolean, by: String?): RemoteOverride {
        val current = _overrides.value.find { it.id == id } ?: throw IllegalArgumentException("no such override")
        val updated = current.copy(paused = paused, pausedChangedAt = Instant.now().toString(), pausedChangedBy = by)
        _overrides.update { list -> list.map { if (it.id == id) updated else it } }
        return updated
    }

    fun deleteOverride(id: String) = _overrides.update { list -> list.filterNot { it.id == id } }

    /** Drops expired overrides; called on every lookup and by the screen now and then. */
    fun prune() {
        val now = Instant.now()
        if (_overrides.value.any { it.expired(now) }) _overrides.update { list -> list.filterNot { it.expired(now) } }
    }

    /** When the host stops, overrides set "until I stop it" end with it. */
    fun onHostStopped() = _overrides.update { list -> list.filter { it.expiresAt != null } }

    /**
     * The overrides that apply to a request from terminal [tid]: one general and one settlement
     * reject at most. One set for this TID comes first, then one set for every terminal. Paused
     * and expired ones are skipped.
     */
    fun activeFor(tid: String?): ActiveLocalOverrides {
        prune()
        val live = _overrides.value.filter { !it.paused }
        fun pick(kind: String) = live.find { it.kind == kind && tid != null && it.tid == tid } ?: live.find { it.kind == kind && it.tid == ALL_TERMINALS }
        return ActiveLocalOverrides(pick("general"), pick("rejectSettlement"))
    }

    private fun RemoteOverride.expired(now: Instant) = expiresAt?.let { runCatching { Instant.parse(it) <= now }.getOrDefault(false) } ?: false

    private fun sorted(list: List<RemoteOverride>) = list.sortedWith(compareBy({ it.tid != ALL_TERMINALS }, { it.tid }, { it.kind }))

    companion object {
        const val MAX_TRANSACTIONS = 2000
        private val byGateway = ConcurrentHashMap<String, LocalHostState>()

        fun of(gatewayId: String): LocalHostState = byGateway.getOrPut(gatewayId) { LocalHostState() }

        /** One request and its reply as a transaction record, in the remote host's format. */
        fun record(
            request: Iso8583Data?,
            requestBytes: ByteArray?,
            response: Iso8583Data?,
            responseBytes: ByteArray?,
            receivedAt: Instant,
            respondedAt: Instant?,
            remote: String,
            rule: String?,
            disposition: String,
            override: RemoteOverride?,
        ): RemoteTxn {
            fun field(m: Iso8583Data?, n: Int) = m?.takeIf { it.bitAttributes.getOrNull(n - 1)?.isSet == true }?.getValue(n - 1)?.trim()?.ifBlank { null }
            return RemoteTxn(
                id = UUID.randomUUID().toString(),
                createdAt = receivedAt.toString(),
                respondedAt = respondedAt?.toString(),
                durationMs = respondedAt?.let { it.toEpochMilli() - receivedAt.toEpochMilli() } ?: 0,
                remote = remote,
                tid = field(request, 41),
                mid = field(request, 42),
                mti = request?.messageType?.takeIf { it.isNotBlank() }?.padStart(4, '0'),
                processingCode = field(request, 3),
                stan = field(request, 11),
                invoice = field(request, 62),
                batch = batchNumber(field(request, 60)),
                responseCode = if (respondedAt != null) field(response, 39) else null,
                rule = rule,
                disposition = disposition,
                overrideId = override?.id,
                overrideKind = override?.kind,
                request = message(request, requestBytes),
                response = if (respondedAt != null) message(response, responseBytes) else null,
            )
        }

        /** F60 as a batch number: 6 digits, or the last 6 of 8. In a 0320 upload it is the original MTI and STAN. */
        fun batchNumber(f60: String?): String? {
            val v = f60?.trim().orEmpty()
            return when {
                v.length == 6 && v.all(Char::isDigit) -> v
                v.length == 8 && v.all(Char::isDigit) -> v.drop(2)
                else -> null
            }
        }

        private fun message(m: Iso8583Data?, bytes: ByteArray?): RemoteMessage? {
            m ?: return null
            val fields = m.bitAttributes.indices
                .filter { it > 0 && m.bitAttributes[it].isSet }
                .map { RemoteField(it + 1, m.getValue(it) ?: "") }
            return RemoteMessage(
                mti = m.messageType.padStart(4, '0'),
                tpdu = m.tpduHeader.rawTPDU.takeIf { it.size == 5 }?.joinToString("") { "%02X".format(it) },
                hex = (bytes ?: runCatching { m.pack() }.getOrNull() ?: byteArrayOf()).joinToString("") { "%02X".format(it) },
                fields = fields,
            )
        }
    }
}

data class ActiveLocalOverrides(val general: RemoteOverride?, val rejectSettlement: RemoteOverride?)

/**
 * The Local Host screen's view of one gateway: its transactions filtered to the terminal the tester
 * is watching, and its overrides. History stays in memory while the app runs.
 */
class LocalHostFeed(private val state: LocalHostState) : HostFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val filter = MutableStateFlow(TerminalFilter())

    override val transactions: StateFlow<List<RemoteTxn>> = combine(state.transactions, filter) { txns, f ->
        txns.filter { t -> (f.tid.isBlank() || t.tid == f.tid) && (f.mid.isBlank() || t.mid == f.mid) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val olderTransactionsCursor: StateFlow<String?> = MutableStateFlow(null)
    override val overrides: StateFlow<List<RemoteOverride>> = state.overrides

    fun changeFilter(f: TerminalFilter) {
        filter.value = f
    }

    override suspend fun loadOlderTransactions() = Unit

    override suspend fun setOverride(request: OverrideRequest): RemoteOverride = state.setOverride(request)

    override suspend fun pauseOverride(id: String, paused: Boolean, by: String?): RemoteOverride = state.pauseOverride(id, paused, by)

    override suspend fun deleteOverride(id: String) = state.deleteOverride(id)

    fun close() = scope.cancel()
}
