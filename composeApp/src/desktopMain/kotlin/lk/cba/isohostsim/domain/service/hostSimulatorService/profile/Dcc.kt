package lk.cba.isohostsim.domain.service.hostSimulatorService.profile

import kotlinx.serialization.Serializable
import java.math.BigInteger

/**
 * The offer a DCC quotation reply carries: the cardholder's currency and the rate. See [Dcc].
 */
@Serializable
data class DccQuote(
    /** Cardholder currency, ISO 4217 letters, e.g. "USD". */
    val currency: String,
    /** The same currency's ISO 4217 number, e.g. "840". */
    val currencyNumeric: String,
    /** Decimals of the cardholder currency (USD 2, JPY 0). */
    val exponent: Int = 2,
    /** Cardholder currency per 1 unit of merchant currency, e.g. "0.0033333" (LKR 1 = USD 0.0033333). */
    val rate: String,
    /** Commission percent, shown on the receipt, e.g. "0.000". */
    val commission: String = "0.000",
    /** Markup percent, shown on the receipt, e.g. "3.500". */
    val markup: String = "0.000",
    /** Decimals of the merchant currency in F4 (LKR 2). */
    val localExponent: Int = 2,
)

/**
 * DCC (Dynamic Currency Conversion) quotation replies, after the Monex DCS ISO 8583 Host Interface
 * v1.4 and checked against what HNB 1.07, SBC and CBC v2 actually parse. The same rules as the
 * remote (Node) host's src/profile/dcc.ts, so both hosts send the same bytes.
 *
 * The terminal sends a rate inquiry (0100, processing code 000000) with the local amount in F4. The
 * reply carries F39 00 and the offer in F63 as one "DC" table of key=value pairs:
 *
 *   DCchca=USD;chcn=840;roex=70033333;chtv=2000000000333;comn=0.000;mrkp=3.500;
 *
 * - The terminals drop the first 2 characters (the table id "DC") and the last one, so the block ends
 *   with ";". The spec's own 4-digit table length is not sent: HNB would read it as part of a key.
 * - CBC v2 reads the six items by position, so the order above is fixed.
 * - roex: 1 digit for the number of decimals, then 7 digits of rate (spec 1.4, Visa F10).
 * - chtv: 1 digit for the cardholder currency exponent, then 12 digits of amount in minor units.
 *   HNB sends those 12 digits as F4 of the DCC sale that follows.
 * - comn / mrkp: 5 characters, "9.999". Only shown on the receipt.
 * F6, F10 and F51 are never sent: they are not in the terminals' bit templates, and an unknown field
 * makes the whole reply unreadable, which the terminal treats as "no DCC".
 */
object Dcc {
    /** The field the offer travels in. */
    const val FIELD = 63

    /** F39 for "DCC not available, authorise in merchant currency" (spec 6.5.6). */
    const val NOT_AVAILABLE = "99"

    /** The longest block: "DC" + six items with 3-letter currency, 8-digit roex, 13-digit chtv. */
    const val MAX_BLOCK_LENGTH = 75

    private val RATE = Regex("""^(\d+)(?:\.(\d+))?$""")
    private val PERCENT = Regex("""^\d\.\d{3}$""")

    private class Rate(val digits: BigInteger, val decimals: Int)

    private fun parseRate(rate: String): Rate? {
        val m = RATE.matchEntire(rate.trim()) ?: return null
        val frac = m.groupValues[2]
        return Rate(BigInteger(m.groupValues[1] + frac), frac.length)
    }

    /** Problems with a profile's `dcc` block, each naming the key. */
    fun check(q: DccQuote): List<String> {
        val errors = mutableListOf<String>()
        if (!q.currency.matches(Regex("[A-Z]{3}"))) errors += "currency: \"${q.currency}\" must be 3 capital letters (ISO 4217), e.g. \"USD\""
        if (!q.currencyNumeric.matches(Regex("\\d{3}"))) errors += "currencyNumeric: \"${q.currencyNumeric}\" must be 3 digits (ISO 4217), e.g. \"840\""
        if (q.exponent !in 0..3) errors += "exponent: must be 0, 1, 2 or 3"
        if (q.localExponent !in 0..3) errors += "localExponent: must be 0, 1, 2 or 3"
        val r = parseRate(q.rate)
        when {
            r == null || r.digits.signum() == 0 -> errors += "rate: \"${q.rate}\" must be a positive decimal number, e.g. \"0.0033333\""
            r.decimals > 9 -> errors += "rate: \"${q.rate}\" has more than 9 decimals"
            r.digits >= BigInteger.valueOf(10_000_000) -> errors += "rate: \"${q.rate}\" has more than 7 significant digits, which the 8-digit roex can't carry"
        }
        if (!PERCENT.matches(q.commission)) errors += "commission: \"${q.commission}\" must look like \"3.500\" (1 digit, a point, 3 digits)"
        if (!PERCENT.matches(q.markup)) errors += "markup: \"${q.markup}\" must look like \"3.500\" (1 digit, a point, 3 digits)"
        return errors
    }

    /** The rate as roex: number of decimals, then 7 digits. */
    fun encodeRate(rate: String): String {
        val r = parseRate(rate)!!
        return r.decimals.toString() + r.digits.toString().padStart(7, '0')
    }

    /**
     * The local amount (minor units) converted to the cardholder currency (minor units), rounded half
     * up. The rate is cardholder currency per 1 unit of merchant currency, as the HNB receipt prints it
     * ("LKR 1 = USD 0.0033333").
     */
    fun cardholderAmount(localMinor: BigInteger, q: DccQuote): BigInteger {
        val r = parseRate(q.rate)!!
        val num = localMinor * r.digits * BigInteger.TEN.pow(q.exponent)
        val den = BigInteger.TEN.pow(r.decimals + q.localExponent)
        return (num * BigInteger.TWO + den) / (den * BigInteger.TWO)
    }

    /**
     * The F63 block for a local amount, or null when the amount is missing, not digits, or converts
     * to more than 12 digits.
     */
    fun block(localAmount: String?, q: DccQuote): String? {
        val local = localAmount?.trim()
        if (local.isNullOrEmpty() || !local.all { it in '0'..'9' }) return null
        val amount = cardholderAmount(BigInteger(local), q).toString()
        if (amount.length > 12) return null
        val items = listOf(
            "chca=${q.currency}",
            "chcn=${q.currencyNumeric}",
            "roex=${encodeRate(q.rate)}",
            "chtv=${q.exponent}${amount.padStart(12, '0')}",
            "comn=${q.commission}",
            "mrkp=${q.markup}",
        )
        return "DC${items.joinToString(";")};"
    }
}
