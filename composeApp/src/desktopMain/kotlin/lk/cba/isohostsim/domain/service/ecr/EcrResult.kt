package lk.cba.isohostsim.domain.service.ecr

import org.json.JSONObject

enum class EcrOutcome {
    /** Final status A (card) or S (QR). */
    APPROVED,
    /** Final status R, explained by `rspCode`. */
    REJECTED,
    /** The terminal refused the command before running it. */
    TERMINAL_NAK,
    /** The library gave up: no route, no port, no ACK, timeout and so on. */
    LIBRARY_NAK,
    /** The terminal only acknowledged, as it does for Check Status. */
    ACKNOWLEDGED,
    /** A reply the app could not classify, or an exception in the library. */
    UNKNOWN,
}

/** One finished ECR exchange: what was sent, what came back, and what it means. */
data class EcrResult(
    val command: EcrCommand,
    val request: String?,
    val response: String,
    val outcome: EcrOutcome,
    val code: String?,
    val message: String,
    /** Flat `data` fields of the final reply, as strings, for the result card. */
    val fields: Map<String, String>,
    val startedAtMs: Long,
    val durationMs: Long,
) {
    val succeeded: Boolean get() = outcome == EcrOutcome.APPROVED || outcome == EcrOutcome.ACKNOWLEDGED

    companion object {

        /** Classifies a reply returned by the CBA ECR library. */
        fun from(
            command: EcrCommand,
            request: JSONObject?,
            response: JSONObject,
            startedAtMs: Long,
            durationMs: Long,
        ): EcrResult {
            val dataType = response.optString("dataType")
            val data = response.optJSONObject("data")
            val fields = data?.let { obj -> obj.keySet().associateWith { key -> obj.opt(key).toFieldString() } }
                .orEmpty()

            val (outcome, code, message) = when {
                dataType.equals("NAK", ignoreCase = true) -> {
                    val error = response.opt("error")?.toString().orEmpty()
                    Triple(
                        if (EcrCodes.isLibraryNak(error)) EcrOutcome.LIBRARY_NAK else EcrOutcome.TERMINAL_NAK,
                        error,
                        EcrCodes.describeNak(error),
                    )
                }
                data != null && data.has("status") -> {
                    val status = data.optString("status")
                    val rspCode = data.opt("rspCode")?.takeUnless { it == JSONObject.NULL }?.toString()
                    val rspText = data.opt("rspText")?.takeUnless { it == JSONObject.NULL }?.toString()
                    when (status) {
                        "A", "S" -> Triple(EcrOutcome.APPROVED, rspCode, rspText ?: "Approved")
                        "R" -> Triple(
                            EcrOutcome.REJECTED,
                            rspCode,
                            listOfNotNull(EcrCodes.describeResponse(rspCode), rspText).distinct().joinToString(" – "),
                        )
                        else -> Triple(EcrOutcome.UNKNOWN, rspCode, "Unrecognised status '$status'")
                    }
                }
                dataType.equals("ACK", ignoreCase = true) || dataType == command.wireName ->
                    Triple(EcrOutcome.ACKNOWLEDGED, null, "Terminal acknowledged")
                else -> Triple(EcrOutcome.UNKNOWN, null, "Unrecognised reply")
            }

            return EcrResult(
                command = command,
                request = request?.toString(),
                response = response.toString(),
                outcome = outcome,
                code = code,
                message = message,
                fields = fields,
                startedAtMs = startedAtMs,
                durationMs = durationMs,
            )
        }

        private fun Any?.toFieldString(): String = when (this) {
            null, JSONObject.NULL -> ""
            else -> toString()
        }
    }
}
