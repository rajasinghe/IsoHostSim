package lk.cba.isohostsim.domain.service.remoteHost

import kotlinx.coroutines.flow.StateFlow

/**
 * The traffic and overrides of one host, local or remote. The Transactions table and the Overrides
 * tab read and change a host through this, so the Local Host and Remote Host screens share them.
 */
interface HostFeed {
    /** Newest first, already filtered to the terminal the tester is watching. */
    val transactions: StateFlow<List<RemoteTxn>>

    /** Non-null when older transactions can be loaded with [loadOlderTransactions]. */
    val olderTransactionsCursor: StateFlow<String?>

    val overrides: StateFlow<List<RemoteOverride>>

    suspend fun loadOlderTransactions()

    suspend fun setOverride(request: OverrideRequest): RemoteOverride

    /** Pauses or resumes an override; a paused one stays listed but the terminal gets normal replies. */
    suspend fun pauseOverride(id: String, paused: Boolean, by: String? = null): RemoteOverride

    suspend fun deleteOverride(id: String)
}
