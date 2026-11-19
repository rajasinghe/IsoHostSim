package lk.cba.isohostsim.domain.service.hostSimulatorService.profile

import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.data.model.GatewayType

/**
 * The host profile built into the app: what the Local Host answers.
 *
 * It is a developer setting. Testers can't pick, open or edit it in the app; a developer changes
 * composeApp/src/desktopMain/resources/host-profile/all-apps.json and rebuilds. Every server host
 * gets it when the app loads, so a rebuilt app answers with the new rules straight away.
 */
object BuiltInHostProfile {
    const val RESOURCE = "/host-profile/all-apps.json"

    /** The checked profile. A broken file is a build mistake, so it fails loudly (and in the tests). */
    val profile: HostProfile by lazy {
        val text = BuiltInHostProfile::class.java.getResource(RESOURCE)?.readText()
            ?: error("The built-in host profile $RESOURCE is missing from the app")
        when (val result = HostProfileLoader.parse(text)) {
            is ProfileResult.Ok -> result.profile
            is ProfileResult.Invalid -> error("The built-in host profile $RESOURCE has problems:\n" + result.errors.joinToString("\n"))
        }
    }

    /** A new server host that answers from the built-in profile. */
    fun newConfig(id: String): GatewayConfig = HostProfileLoader.toGatewayConfig(profile, id, null)

    /**
     * Puts the built-in profile into [cfg]. The tester's port is kept; the address is set when the
     * host starts. Any link to a profile file is dropped.
     */
    fun applyTo(cfg: GatewayConfig) {
        val port = cfg.serverPort
        HostProfileLoader.applyTo(cfg, profile, null)
        if (port > 0) cfg.serverPort = port
    }

    /** Gives every server host the built-in profile, adding one when there is none. */
    fun install(hosts: List<GatewayConfig>, newId: () -> String): List<GatewayConfig> {
        hosts.filter { it.gatewayType == GatewayType.SERVER }.forEach(::applyTo)
        return if (hosts.any { it.gatewayType == GatewayType.SERVER }) hosts else hosts + newConfig(newId())
    }
}
