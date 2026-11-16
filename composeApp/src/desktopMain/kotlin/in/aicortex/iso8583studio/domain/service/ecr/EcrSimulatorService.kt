package `in`.aicortex.iso8583studio.domain.service.ecr

import `in`.aicortex.iso8583studio.data.StudioPaths
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
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
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate

/** What the ECR console is doing right now. */
sealed interface EcrRunState {
    data object Idle : EcrRunState
    data class Running(val command: EcrCommand, val startedAtMs: Long) : EcrRunState
}

/**
 * One ECR Simulator session: drives a terminal through [EcrLibraryClient] using the settings in
 * a saved [EcrSimulatorConfig].
 *
 * Mirrors the CBA ECR demo app: the ECR reference goes up by one after every approved sale, a
 * BIN request's `binReferenceNo` is kept for the next BIN sale, and "BIN + Sale" chains
 * Check Status → BIN Request → Sale.
 */
class EcrSimulatorService(
    initialConfig: EcrSimulatorConfig,
    private val client: EcrLibraryClient = EcrLibraryClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<EcrSimulatorConfig> = _config.asStateFlow()

    private val _state = MutableStateFlow<EcrRunState>(EcrRunState.Idle)
    val state: StateFlow<EcrRunState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<EcrResult>>(emptyList())
    /** Finished exchanges, newest first. */
    val history: StateFlow<List<EcrResult>> = _history.asStateFlow()

    private val _lastBinReference = MutableStateFlow("")
    val lastBinReference: StateFlow<String> = _lastBinReference.asStateFlow()

    private val _libraryLog = MutableStateFlow("")
    /** The tail of the library's own log file for today. */
    val libraryLog: StateFlow<String> = _libraryLog.asStateFlow()

    private var logTail: Job? = null

    /** Called with the updated profile whenever the next ECR reference changes, to persist it. */
    var onConfigChanged: ((EcrSimulatorConfig) -> Unit)? = null

    val target: EcrTarget
        get() = _config.value.let { EcrTarget(it.transport, it.terminalIp, it.port, it.usbPortOverride) }

    fun updateConfig(config: EcrSimulatorConfig) {
        _config.value = config
    }

    fun startLogTail() {
        if (logTail?.isActive == true) return
        logTail = scope.launch {
            while (isActive) {
                _libraryLog.value = readLibraryLogTail()
                delay(1_000)
            }
        }
    }

    suspend fun checkStatus(): EcrResult = run(EcrCommand.CHECK_STATUS, null)

    suspend fun binRequest(): EcrResult = run(EcrCommand.BIN_REQUEST, EcrRequests.binRequest()).also { result ->
        result.fields["binReferenceNo"]?.takeIf { it.isNotBlank() }?.let { _lastBinReference.value = it }
    }

    suspend fun sale(fields: EcrSaleFields): EcrResult =
        run(EcrCommand.SALE, EcrRequests.sale(fields)).also { result ->
            if (result.outcome == EcrOutcome.APPROVED && result.code == "00") advanceEcrRef(fields.ecrRef)
        }

    /** Check Status, then BIN Request, then a Sale carrying the new BIN reference. */
    suspend fun binSale(fields: EcrSaleFields): List<EcrResult> {
        val status = checkStatus()
        if (!status.succeeded) return listOf(status)
        val bin = binRequest()
        if (bin.outcome != EcrOutcome.APPROVED) return listOf(status, bin)
        val sale = sale(fields.copy(binReference = _lastBinReference.value))
        return listOf(status, bin, sale)
    }

    suspend fun void(fields: EcrVoidFields): EcrResult = run(EcrCommand.VOID, EcrRequests.void(fields))

    suspend fun requestLast(detail: String): EcrResult =
        run(EcrCommand.REQUEST_LAST, EcrRequests.requestLast(detail))

    suspend fun transferKey(key: String): EcrResult =
        run(EcrCommand.TRANSFER_KEY, EcrRequests.transferKey(key))

    suspend fun settlement(): EcrResult = run(EcrCommand.SETTLEMENT, null)

    suspend fun lastSettlement(): EcrResult = run(EcrCommand.LAST_SETTLEMENT, null)

    /** Sends Transaction Cancel while another command is running. */
    suspend fun cancel(): EcrResult = client.cancel(target).also(::record)

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun close() {
        scope.cancel()
        client.close()
    }

    private suspend fun run(command: EcrCommand, request: JSONObject?): EcrResult {
        val cfg = _config.value
        _state.value = EcrRunState.Running(command, clock())
        try {
            val result = client.execute(target, command, request, cfg.encrypted, cfg.timeoutSec)
            record(result)
            return result
        } finally {
            _state.value = EcrRunState.Idle
        }
    }

    private fun record(result: EcrResult) {
        _history.update { (listOf(result) + it).take(MAX_HISTORY) }
    }

    private fun advanceEcrRef(usedRef: String) {
        val next = (usedRef.trim().toLongOrNull() ?: _config.value.nextEcrRef) + 1
        val updated = _config.value.copy(nextEcrRef = next)
        _config.value = updated
        onConfigChanged?.invoke(updated)
    }

    private fun readLibraryLogTail(): String {
        val file = File(StudioPaths.ecrDir, "PAX_EDC_ECR_logs${File.separator}${LocalDate.now()}.txt")
        if (!file.isFile) return ""
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val start = (raf.length() - MAX_LOG_BYTES).coerceAtLeast(0)
                raf.seek(start)
                val bytes = ByteArray((raf.length() - start).toInt())
                raf.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    private companion object {
        const val MAX_HISTORY = 200
        const val MAX_LOG_BYTES = 64L * 1024
    }
}
