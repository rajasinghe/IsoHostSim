package `in`.aicortex.iso8583studio.edition

import `in`.aicortex.iso8583studio.data.model.StudioTool

/**
 * The tools this build shows. The current release ships only the Host and ECR simulators.
 *
 * To work on another tool, uncomment its line in [ENABLED]. Every other tool stays in the code
 * but is never listed or opened: the menus, home screen, Quick Access and search all read this
 * list, and `SimulatorSessionManager.openTool` refuses a tool that is not in it.
 */
object EnabledTools {

    val ENABLED: Set<StudioTool> = setOf(
        StudioTool.HOST_SIMULATOR,                  // Host Simulator
        // StudioTool.HSM_SIMULATOR,                // HSM Simulator
        // StudioTool.HSM_COMMAND,                  // HSM Host Console
        StudioTool.ECR_SIMULATOR,                   // ECR Simulator
        // StudioTool.POS_TERMINAL,                 // POS Terminal
        // StudioTool.ATM_SIMULATOR,                // ATM Simulator
        // StudioTool.PAYMENT_SWITCH,               // Payment Switch
        // StudioTool.ACQUIRER_GATEWAY,             // Acquirer Gateway
        // StudioTool.ISSUER_SYSTEM,                // Issuer System
        // StudioTool.APDU_SIMULATOR,               // APDU Simulator
        // StudioTool.EMV_41_CRYPTO,                // EMV 4.1 Crypto
        // StudioTool.EMV_42_CRYPTO,                // EMV 4.2 Crypto
        // StudioTool.MASTERCARD_CRYPTO,            // MasterCard Crypto
        // StudioTool.VSDC_CRYPTO,                  // VSDC Crypto
        // StudioTool.SDA_VERIFICATION,             // SDA Verification
        // StudioTool.DDA_VERIFICATION,             // DDA Verification
        // StudioTool.CAP_TOKEN,                    // CAP Token
        // StudioTool.HCE_VISA,                     // HCE Visa
        // StudioTool.SECURE_MESSAGING,             // Secure Messaging
        // StudioTool.ATR_PARSER,                   // ATR Parser
        // StudioTool.EMV_DATA_PARSER,              // EMV Data Parser
        // StudioTool.EMV_TAG_DICTIONARY,           // EMV Tag Dictionary
        // StudioTool.AES_CALCULATOR,               // AES Calculator
        // StudioTool.DES_CALCULATOR,               // DES Calculator
        // StudioTool.RSA_CALCULATOR,               // RSA Calculator
        // StudioTool.ECDSA_CALCULATOR,             // ECDSA Calculator
        // StudioTool.HASH_CALCULATOR,              // Hash Calculator
        // StudioTool.THALES_RSA,                   // Thales RSA
        // StudioTool.FPE_CALCULATOR,               // FPE Calculator
        // StudioTool.DEA_KEYS,                     // DEA Keys
        // StudioTool.KEYSHARE_GENERATOR,           // Keyshare Generator
        // StudioTool.THALES_KEYS,                  // Thales Keys
        // StudioTool.FUTUREX_KEYS,                 // Futurex Keys
        // StudioTool.ATALLA_KEYS,                  // Atalla Keys
        // StudioTool.SAFENET_KEYS,                 // SafeNet Keys
        // StudioTool.THALES_KEY_BLOCKS,            // Thales Key Blocks
        // StudioTool.TR31_KEY_BLOCKS,              // TR-31 Key Blocks
        // StudioTool.SSL_CERTIFICATE,              // SSL Certificate
        // StudioTool.RSA_DER_KEYS,                 // RSA DER Keys
        // StudioTool.CVV_CALCULATOR,               // CVV Calculator
        // StudioTool.AMEX_CSC,                     // AMEX CSC
        // StudioTool.MASTERCARD_CSC,               // MasterCard CVC
        // StudioTool.DUKPT_ISO_9797,               // DUKPT ISO 9797
        // StudioTool.DUKPT_ISO_AES,                // DUKPT ISO AES
        // StudioTool.ISO_IES_9797_1_MAC,           // ISO/IEC 9797-1
        // StudioTool.ANSI_MAC,                     // ANSI X9.9 & X9.19
        // StudioTool.AS2805_MAC,                   // AS2805.4.1 MAC Calculator
        // StudioTool.TDES_CBC_MAC,                 // TDES-CBC MAC Calculator
        // StudioTool.HMAC_MAC,                     // HMAC MAC Calculator
        // StudioTool.CMAC_MAC,                     // CMAC MAC Calculator
        // StudioTool.RETAIL_MAC,                   // Retail MAC Calculator
        // StudioTool.MDC_HASH,                     // MDC Hash Calculator
        // StudioTool.PIN_BLOCK_GENERAL,            // PIN Block Calculator
        // StudioTool.PIN_BLOCK_AES,                // PIN Block(AES) Calculator
        // StudioTool.PIN_OFFSET_IBM,               // PIN Offset (IBM 3624)
        // StudioTool.PIN_PVV,                      // PIN PVV Calculator
        // StudioTool.ZKA,                          // ZKA
        // StudioTool.AS2805_CALCULATOR,            // AS2805 Calculator
        // StudioTool.BITMAP_CALCULATOR,            // Bitmap Calculator
        // StudioTool.MESSAGE_PARSER,               // Message Parser
        // StudioTool.BASE64_ENCODER,               // Base64 Encoder
        // StudioTool.BASE94_ENCODER,               // Base94 Encoder
        // StudioTool.BCD_CONVERTER,                // BCD Converter
        // StudioTool.CHARACTER_ENCODER,            // Character Encoder
        // StudioTool.CHECK_DIGIT,                  // Check Digit
        // StudioTool.APDU_RESPONSE_QUERY,          // APDU Response Query
        // StudioTool.TRACK2_CODEC,                 // Track 2 Codec
    )

    /** Analytics and its first-run consent dialog stay off for this internal release. */
    const val analyticsEnabled: Boolean = false

    fun isEnabled(tool: StudioTool): Boolean = tool in ENABLED

    fun anyEnabled(vararg tools: StudioTool): Boolean = tools.any(::isEnabled)

    /** Enabled tools in their [StudioTool] order. */
    fun visibleTools(): List<StudioTool> = StudioTool.entries.filter(::isEnabled)
}
