package lk.cba.isohostsim.hsm.payshield10k.data


/**
 * Key Status
 */
enum class KeyStatus {
    ACTIVE,
    EXPIRED,
    REVOKED,
    PENDING_ACTIVATION,
    COMPROMISED
}