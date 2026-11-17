package lk.cba.isohostsim.hsm.payshield10k.data


/**
 * HSM State enumeration
 */
enum class HsmState {
    OFFLINE,        // Initial state, LMK not loaded
    ONLINE,         // LMK loaded, ready for operations
    SECURE,         // Secure configuration mode
    AUTHORIZED      // Temporarily authorized for sensitive operations
}