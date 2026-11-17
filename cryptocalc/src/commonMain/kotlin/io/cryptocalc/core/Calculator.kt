package io.cryptocalc.core

import lk.cba.isocore.types.CalculatorCategory
import lk.cba.isocore.types.CalculatorInput
import lk.cba.isocore.types.CalculatorResult
import lk.cba.isocore.types.CipherMode
import lk.cba.isocore.types.KeySize
import lk.cba.isocore.types.OperationType
import lk.cba.isocore.types.PaddingMethods

internal interface Calculator<T: CalculatorInput, R: CalculatorResult>  {
    val id: String
    val name: String
    val category: CalculatorCategory
    val version: String

    suspend fun execute(input: T): R
    fun validate(input: T): R
    fun getSchema(): CalculatorSchema
    fun getCapabilities(): CalculatorCapabilities
}

data class CalculatorSchema(
    val requiredParameters: List<ParameterSchema>,
    val optionalParameters: List<ParameterSchema> = emptyList(),
    val supportedOperations: List<OperationType>
)

data class ParameterSchema(
    val name: String,
    val type: ParameterType,
    val description: String,
    val validation: ParameterValidation? = null
)

enum class ParameterType {
    STRING, HEX_STRING, BASE64_STRING, INTEGER, BOOLEAN, BYTE_ARRAY
}

data class ParameterValidation(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val allowedValues: List<String>? = null
)

data class CalculatorCapabilities(
    val supportedAlgorithms: List<String> = emptyList(),
    val supportedModes: List<CipherMode> = emptyList(),
    val supportedPadding: List<PaddingMethods> = emptyList(),
    val supportedKeySizes: List<KeySize> = emptyList(),
    val maxConcurrentOperations: Int = 10
)
