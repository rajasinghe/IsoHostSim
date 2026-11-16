package `in`.aicortex.iso8583studio.domain.service.ecr

import org.json.JSONObject

/** Sale form values. Every value is sent as a string, exactly as the ECR demo app does. */
data class EcrSaleFields(
    val amt: String,
    val tipAmt: String = "0",
    val currency: String = "LKR",
    val detail: String = "Y",
    val ecrRef: String = "1",
    val print: String = "Y",
    val txnConsequentialNo: String = "",
    val binReference: String = "",
    val mobileNo: String = "",
    val email: String = "",
)

data class EcrVoidFields(
    val invoiceNo: String,
    val pwd: String,
    val detail: String = "Y",
    val print: String = "Y",
    val mobileNo: String = "",
    val email: String = "",
)

/**
 * Builds the request objects the CBA ECR library expects: `{"dataType": ..., "data": {...}}`.
 * The library validates `dataType` and adds the checksum itself. Key names, including the
 * terminal's own spellings (`Currency`, `BinRefferance`), match the demo app's MainJFrame.
 */
object EcrRequests {

    fun binRequest(): JSONObject = envelope(EcrCommand.BIN_REQUEST) {
        put("function", "binRequest")
    }

    fun sale(f: EcrSaleFields): JSONObject = envelope(EcrCommand.SALE) {
        put("amt", f.amt)
        put("tipAmt", f.tipAmt)
        put("Currency", f.currency)
        put("detail", f.detail)
        put("ecrRef", f.ecrRef)
        put("print", f.print)
        put("txnConsequentialNo", f.txnConsequentialNo)
        put("BinRefferance", f.binReference)
        put("mobileNo", f.mobileNo)
        put("email", f.email)
    }

    fun void(f: EcrVoidFields): JSONObject = envelope(EcrCommand.VOID) {
        put("invoiceNo", f.invoiceNo)
        put("detail", f.detail)
        put("print", f.print)
        put("pwd", f.pwd)
        put("mobileNo", f.mobileNo)
        put("email", f.email)
    }

    fun requestLast(detail: String): JSONObject = envelope(EcrCommand.REQUEST_LAST) {
        put("detail", detail)
    }

    fun transferKey(key: String): JSONObject = envelope(EcrCommand.TRANSFER_KEY) {
        put("Key", key)
    }

    private fun envelope(command: EcrCommand, fill: JSONObject.() -> Unit): JSONObject =
        JSONObject().apply {
            put("dataType", command.wireName)
            put("data", JSONObject().apply(fill))
        }
}
