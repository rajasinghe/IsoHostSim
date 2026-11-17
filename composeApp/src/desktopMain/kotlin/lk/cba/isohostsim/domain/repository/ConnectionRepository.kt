package lk.cba.isohostsim.domain.repository

import lk.cba.isohostsim.data.model.ConnectionSettings


/**
 * Repository interface for network connections
 */
interface ConnectionRepository {
    /**
     * Get available network interfaces
     */
    suspend fun getNetworkInterfaces(): List<String>

    /**
     * Get available COM ports
     */
    suspend fun getComPorts(): List<String>

    /**
     * Test a connection
     */
    suspend fun testConnection(connectionSettings: ConnectionSettings): Result<Boolean>
}