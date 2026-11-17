package io.cryptocalc.crypto.engines.encryption

import lk.cba.isocore.types.AlgorithmType
import lk.cba.isocore.types.CipherMode
import lk.cba.isocore.types.CryptoAlgorithm
import io.cryptocalc.crypto.engines.Engine
import io.cryptocalc.crypto.engines.encryption.models.DecryptionEngineParameters
import io.cryptocalc.crypto.engines.encryption.models.EncryptionEngineParameters

interface EncryptionEngine : Engine {
    suspend fun <T : AlgorithmType> encrypt(
        algorithm: CryptoAlgorithm<T>,
        encryptionEngineParameters: EncryptionEngineParameters<T>,
    ): ByteArray

    suspend fun <T : AlgorithmType> decrypt(
        algorithm: CryptoAlgorithm<T>,
        decryptionEngineParameters: DecryptionEngineParameters<T>
    ): ByteArray

}