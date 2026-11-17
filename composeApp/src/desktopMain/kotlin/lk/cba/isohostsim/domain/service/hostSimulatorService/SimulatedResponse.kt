package lk.cba.isohostsim.domain.service.hostSimulatorService

import lk.cba.isohostsim.data.Iso8583Data
import lk.cba.isohostsim.data.TPDU
import lk.cba.isohostsim.data.applyTemplate
import lk.cba.isohostsim.data.updateBit
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.HttpMethod
import lk.cba.isohostsim.ui.screens.hostSimulator.MatchOperator
import lk.cba.isohostsim.ui.screens.hostSimulator.Transaction

class SimulatedResponse(
    val dataRequest: Iso8583Data?,
    val config: GatewayConfig,
    val isFirst: Boolean
) {
    private var isoData: Iso8583Data? = null

    init {

        val transaction = getMatchedSimulatedTransaction()
        transaction?.let {
            // The simulation section decides how often [SIMRC] resolves to a decline; the template
            // decides which field carries it.
            val processedTransaction = PlaceholderProcessor.processPlaceholders(
                it.fields!!.toTypedArray(),
                dataRequest?.bitAttributes,
                simulation = config.simulation.responseCode
            )
            val response = Iso8583Data(
                template = processedTransaction,
                config = config,
                isFirst = isFirst
            )
            response.httpInfo = dataRequest?.httpInfo
            if(it.responseMapping.enabled){
                response.otherKAttributes = PlaceholderProcessor.processPlaceholders(
                    requestTransaction = dataRequest?.otherKAttributes,
                    responseFields = it.responseMapping.responseFields,
                    simulation = config.simulation.responseCode
                )
            }
            response.messageType = it.responseMti?.takeIf { mti -> mti.isNotBlank() }
                ?: dataRequest?.messageType?.toIntOrNull()?.plus(10)?.toString() ?: ""
            isoData = response
        }

        if (transaction == null) {
            config.unmatchedResponseCode?.takeIf { it.isNotBlank() }?.let { code ->
                isoData = buildDecline(code)
            }
        }

        if (config.swapResponseTpdu) {
            dataRequest?.tpduHeader?.rawTPDU?.takeIf { it.size == 5 }?.let { tpdu ->
                isoData?.tpduHeader = TPDU(rawTPDU = swapTpdu(tpdu))
            }
        }
    }

    /**
     * A decline for a request no transaction matches: the request's identifying fields echoed back
     * and field 39 set to [code], so the terminal gets a proper ISO 8583 answer it can show.
     */
    private fun buildDecline(code: String): Iso8583Data? {
        val request = dataRequest ?: return null
        val response = Iso8583Data(config, isFirst)
        val template = config.getBitTemplate(isFirst) ?: emptyArray()
        DECLINE_ECHO_FIELDS.forEach { field ->
            val src = request.bitAttributes.getOrNull(field - 1) ?: return@forEach
            if (!src.isSet || src.data == null) return@forEach
            response.bitAttributes[field - 1].apply {
                applyTemplate(template, field)
                lengthAttribute = src.lengthAttribute
                maxLength = src.maxLength
                typeAtribute = src.typeAtribute
                length = src.length
                data = src.data?.copyOf()
                isSet = true
            }
        }
        response.bitAttributes[38].apply {
            applyTemplate(template, 39)
            updateBit(code)
            isSet = true
        }
        response.messageType = request.messageType.toIntOrNull()?.plus(10)?.toString() ?: ""
        return response
    }

    companion object {
        /** Request fields a decline for an unmatched request copies back. */
        val DECLINE_ECHO_FIELDS = listOf(2, 3, 4, 11, 12, 13, 22, 24, 25, 37, 41, 42)

        /** `60 dest(2) origin(2)` → `60 origin(2) dest(2)`. */
        fun swapTpdu(tpdu: ByteArray): ByteArray =
            byteArrayOf(tpdu[0], tpdu[3], tpdu[4], tpdu[1], tpdu[2])
    }

    private fun getMatchedSimulatedTransaction(): Transaction? {
        return config.getSimulatedTransaction(isFirst).firstOrNull {
            (if (it.restApiMatching.enabled) {
                if (it.restApiMatching.pathMatching.enabled) {
                    if (it.restApiMatching.pathMatching.exactMatch) {
                        dataRequest?.httpInfo?.path == it.restApiMatching.pathMatching.path
                                && dataRequest.httpInfo?.method == HttpMethod.valueOf(it.restApiMatching.pathMatching.method)
                                && dataRequest.httpInfo?.queryParams?.map { param ->
                                   param.value == it.restApiMatching.pathMatching.query?.get(param.key)
                        }?.any { it == false } == false
                    } else {
                        val pathMatching = it.restApiMatching.pathMatching.path.split("/")
                        dataRequest?.httpInfo?.path?.split("/")?.mapIndexed { i,requestPath ->
                            if( pathMatching.size > i) {
                                requestPath == pathMatching[i] || pathMatching[i] == "*"
                            } else {
                                false
                            }
                        }?.any{ it == false } == false && dataRequest.httpInfo?.method == HttpMethod.valueOf(it.restApiMatching.pathMatching.method)
                                && dataRequest.httpInfo?.queryParams?.map { param ->
                            param.value == it.restApiMatching.pathMatching.query?.get(param.key) || it.restApiMatching.pathMatching.query?.get(param.key) == "*"
                        }?.any { it == false } == false
                    }
                } else true && it.restApiMatching.keyValueMatching.map { matcher ->
                    if (matcher.key.startsWith("header")) {
                        dataRequest?.httpInfo?.headers?.filter { map ->
                            matcher.key == "header.${map.key}" && matchValues(
                                matcher.value,
                                map.value,
                                matcher.operator
                            )
                        }.isNullOrEmpty() == false
                    } else {
                        dataRequest?.otherKAttributes?.filter { map ->
                            matcher.key == map.key && matchValues(
                                matcher.value,
                                map.value ?: "",
                                matcher.operator
                            )
                        }.isNullOrEmpty() == false
                    }

                }.any { it == false } == false
            } else {
                it.mti == dataRequest?.messageType && (
                        it.proCode == dataRequest.getValue(
                            2
                        ) || it.proCode == "*"
                        )
            }) == true
        }?.copy()

    }

    private fun matchValues(
        s1: String,
        s2: String,
        operator: MatchOperator
    ) =
        when (operator) {
            MatchOperator.EQUALS -> s1 == s2
            MatchOperator.NOT_EQUALS -> s1 != s2
            MatchOperator.STARTS_WITH -> s2.startsWith(s1)
            MatchOperator.ENDS_WITH -> s2.endsWith(s1)
            MatchOperator.CONTAINS -> s2.contains(s1)
            MatchOperator.REGEX -> {
                val regex = Regex(s1)
                regex.matches(s2)
            }

            MatchOperator.GREATER_THAN -> {
                val value1 = s1.toDoubleOrNull()
                val value2 = s2.toDoubleOrNull()
                if (value1 != null && value2 != null) {
                    value2 > value1
                } else {
                    throw IllegalArgumentException("Cannot compare non-numeric values: $s1 and $s2")
                }
            }

            MatchOperator.LESS_THAN -> {
                val value1 = s1.toDoubleOrNull()
                val value2 = s2.toDoubleOrNull()
                if (value1 != null && value2 != null) {
                    value2 < value1
                } else {
                    throw IllegalArgumentException("Cannot compare non-numeric values: $s1 and $s2")
                }
            }
        }


    fun pack(): ByteArray? {
        return isoData?.pack()
    }
}