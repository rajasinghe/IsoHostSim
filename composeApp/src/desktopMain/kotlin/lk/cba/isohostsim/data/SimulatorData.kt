package lk.cba.isohostsim.data

interface SimulatorData {
    var rawMessage: ByteArray

    fun logFormat(): String

    fun unpack(input: ByteArray)

    fun pack(isHttpInfo: Boolean = true): ByteArray
}