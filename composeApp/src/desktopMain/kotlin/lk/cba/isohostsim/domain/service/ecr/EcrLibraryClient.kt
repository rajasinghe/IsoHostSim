package lk.cba.isohostsim.domain.service.ecr

import TCPJAVA_CBA_ClassLibrary.Communication.PaxPort
import TCPJAVA_CBA_ClassLibrary.TCPJAVA_CBA_ClassLibrary
import TCPJAVA_CBA_ClassLibrary.TerminalFrames
import lk.cba.isohostsim.data.StudioPaths
import lk.cba.isohostsim.ui.navigation.stateConfigs.ecr.EcrTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Where the ECR sends its commands. */
data class EcrTarget(
    val transport: EcrTransport,
    val ip: String = "",
    val port: Int = 0,
    /** USB port to force (e.g. `/dev/ttyACM0`); blank lets the library find the PAX port. */
    val usbPort: String = "",
)

/**
 * The only code that calls the CBA ECR library (`TCPJAVA_CBA_ClassLibrary`).
 *
 * The library's calls block until the terminal answers or times out, so they run on
 * [Dispatchers.IO]. A [Mutex] allows one transaction at a time, as the terminal does.
 * [cancel] deliberately skips the mutex: the library requires Transaction Cancel to be sent from
 * a different thread while the transaction is still running.
 */
class EcrLibraryClient(
    private val library: TCPJAVA_CBA_ClassLibrary = TCPJAVA_CBA_ClassLibrary(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    init {
        configureLibraryFolder()
    }

    val isBusy: Boolean get() = mutex.isLocked

    /**
     * Sends [command] and waits for its final reply. [request] is the `{"dataType","data"}` object
     * built by [EcrRequests]; Check Status and the settlement commands take none.
     */
    suspend fun execute(
        target: EcrTarget,
        command: EcrCommand,
        request: JSONObject? = null,
        encrypted: Boolean = false,
        timeoutSec: Int = 70,
    ): EcrResult = mutex.withLock { call(target, command, request, encrypted, timeoutSec) }

    /** Sends Transaction Cancel alongside a running transaction (see the class comment). */
    suspend fun cancel(target: EcrTarget): EcrResult =
        call(target, EcrCommand.TRANSACTION_CANCEL, null, false, 0)

    /** Releases the USB port the library keeps open between commands. */
    fun close() {
        runCatching { TCPJAVA_CBA_ClassLibrary.closeUsb() }
    }

    private suspend fun call(
        target: EcrTarget,
        command: EcrCommand,
        request: JSONObject?,
        encrypted: Boolean,
        timeoutSec: Int,
    ): EcrResult = withContext(Dispatchers.IO) {
        val started = clock()
        // Record what the terminal sends during this call, as received. The library call blocks
        // this thread, so begin/end bracket exactly its frames. //gimhan added
        var frames: List<EcrFrame> = emptyList()
        TerminalFrames.begin()
        val response = try {
            runCatching { invoke(target, command, request, encrypted, timeoutSec) }
        } finally {
            frames = TerminalFrames.end().map { EcrFrame(EcrFrameKind.valueOf(it.kind.name), it.raw) }
        }
        val elapsed = clock() - started
        response.fold(
            onSuccess = { EcrResult.from(command, request, it, started, elapsed).copy(terminalFrames = frames) },
            onFailure = { error ->
                EcrResult(
                    command = command,
                    request = request?.toString(),
                    response = error.toString(),
                    outcome = EcrOutcome.UNKNOWN,
                    code = null,
                    message = "Library error: ${error.message ?: error::class.simpleName}",
                    fields = emptyMap(),
                    startedAtMs = started,
                    durationMs = elapsed,
                    terminalFrames = frames,
                )
            },
        )
    }

    private fun invoke(
        target: EcrTarget,
        command: EcrCommand,
        request: JSONObject?,
        encrypted: Boolean,
        timeoutSec: Int,
    ): JSONObject {
        val tcp = target.transport == EcrTransport.TCP
        if (!tcp) PaxPort.setPreferredPortName(target.usbPort.ifBlank { null })
        val ip = target.ip
        val port = target.port
        fun req() = requireNotNull(request) { "${command.label} needs a request object" }

        val response = when (command) {
            EcrCommand.CHECK_STATUS ->
                if (tcp) library.CheckStatus(ip, port) else library.CheckStatus()
            EcrCommand.BIN_REQUEST ->
                if (tcp) library.RequestBin(ip, port, req(), encrypted, timeoutSec)
                else library.RequestBin(req(), encrypted, timeoutSec)
            EcrCommand.SALE ->
                if (tcp) library.RequestDoSale(ip, port, req(), encrypted, timeoutSec)
                else library.RequestDoSale(req(), encrypted, timeoutSec)
            EcrCommand.VOID ->
                if (tcp) library.DoVoid(ip, port, req(), encrypted, timeoutSec)
                else library.DoVoid(req(), encrypted, timeoutSec)
            EcrCommand.REQUEST_LAST ->
                if (tcp) library.RequestLast(ip, port, req(), encrypted, timeoutSec)
                else library.RequestLast(req(), encrypted, timeoutSec)
            EcrCommand.TRANSFER_KEY ->
                if (tcp) library.RequestTransferKey(ip, port, req(), timeoutSec)
                else library.RequestTransferKey(req(), timeoutSec)
            EcrCommand.SETTLEMENT ->
                if (tcp) library.RequestDoSettlement(ip, port, encrypted) else library.RequestDoSettlement()
            EcrCommand.LAST_SETTLEMENT ->
                if (tcp) library.RequestLastSettlement(ip, port, encrypted) else library.RequestLastSettlement()
            EcrCommand.TRANSACTION_CANCEL ->
                if (tcp) library.RequestTransactionCancel(ip, port) else library.RequestTransactionCancel()
        }
        // A USB write failure (NAK 17) means the cached port is dead, typically after the terminal was
        // unplugged and plugged back in. Flag it so the next command reopens the device instead of
        // failing until the simulator is restarted.
        if (!tcp && response.optString("dataType") == "NAK" && response.opt("error")?.toString() == "17") {
            PaxPort.markStale()
        }
        return response
    }

    companion object {
        /** System property the library reads for its log and key folder (see cba-ecr-lib/PATCHES.md). */
        const val LIBRARY_DIR_PROPERTY = "cba.ecr.dir"

        /** Points the library's logs and key file at `~/.pos-testing-platform/ecr` unless already set. */
        fun configureLibraryFolder() {
            if (System.getProperty(LIBRARY_DIR_PROPERTY).isNullOrBlank()) {
                val dir = StudioPaths.ecrDir
                dir.mkdirs()
                System.setProperty(LIBRARY_DIR_PROPERTY, dir.absolutePath)
            }
        }
    }
}
