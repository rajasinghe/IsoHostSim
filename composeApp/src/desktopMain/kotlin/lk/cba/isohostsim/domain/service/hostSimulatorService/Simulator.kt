package lk.cba.isohostsim.domain.service.hostSimulatorService

import androidx.compose.runtime.Composable
import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.data.KeyManagement
import lk.cba.isohostsim.data.PermanentConnection
import lk.cba.isohostsim.data.ResultDialogInterface
import lk.cba.isohostsim.data.SimulatorConfig
import lk.cba.isohostsim.data.SimulatorData
import lk.cba.isohostsim.logging.LogEntry
import java.time.LocalDateTime

/**
* Gateway service interface that defines the functionality of a security gateway
*/
interface Simulator {

    /**
     * Get the current gateway configuration
     */
    val configuration: SimulatorConfig

    /**
     * Set a new gateway configuration
     */
    fun<T : SimulatorConfig> setConfiguration(config: T)

    /**
     * Start the gateway service
     */
    suspend fun start()

    /**
     * Stop the gateway service
     */
    suspend fun stop()

    /**
     * Check if the gateway is currently running
     */
    fun isStarted(): Boolean

    /**
     * Get the number of active connections
     */
    fun getConnectionCount(): Int

    /**
     * Get the total number of bytes received from clients
     */
    fun getBytesIncoming(): Int

    /**
     * Get the total number of bytes sent to clients
     */
    fun getBytesOutgoing(): Int

    /**
     * Write a log message
     */
    fun writeLog(log: LogEntry)



    /**
     * Add callback for error handling
     */
    fun showError(item:@Composable () -> Unit)
    fun showSuccess(item:@Composable () -> Unit)
    fun showWarning(item:@Composable () -> Unit)

    fun setShowErrorListener(resultDialogInterface: ResultDialogInterface)

    /**
     * Add callback for data sent event
     */
    fun onSentToDest(callback: (SimulatorData?) -> Unit) {}

    /**
     * Add callback for data received event
     */
    fun onSentToSource(callback: (SimulatorData?) -> Unit) {}


    /**
     * Add callback for before write log event
     */
    fun beforeWriteLog(callback: (LogEntry) -> Unit) {}


    /**
     * Add callback for data received from source
     */
    fun onReceiveFromSource(callback:  (SimulatorData?) -> Unit) {}

    /**
     * Add callback for data received from destination
     */
    fun onReceiveFromDest(callback:  (SimulatorData?) -> Unit) {}
}