package lk.cba.isohostsim.domain.service.remoteHost

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import lk.cba.isohostsim.data.StudioPaths
import java.io.File

/**
 * What the remote host (host_sim_backend_node) sends. Field names follow its JSON; anything new it
 * adds later is ignored, so an older app keeps working with a newer host.
 */
@Serializable
data class RemoteField(val field: Int, val value: String)

@Serializable
data class RemoteMessage(
    val mti: String,
    val tpdu: String? = null,
    /** The whole packet including its length header. Card data is masked by the host. */
    val hex: String,
    val fields: List<RemoteField> = emptyList(),
)

@Serializable
data class RemoteTxn(
    val id: String,
    val createdAt: String,
    val respondedAt: String? = null,
    val durationMs: Long = 0,
    val remote: String = "",
    val connectionId: String = "",
    val tid: String? = null,
    val mid: String? = null,
    val mti: String? = null,
    val processingCode: String? = null,
    val stan: String? = null,
    /** The terminal's invoice number (F62). Hosts older than this field leave it out. */
    val invoice: String? = null,
    /** The terminal's batch number (F60), on settlements, settlement ends and echoes. */
    val batch: String? = null,
    val responseCode: String? = null,
    val rule: String? = null,
    /** matched, unmatched, no-reply, override-no-response or parse-error. */
    val disposition: String = "",
    val overrideId: String? = null,
    /** "general" or "rejectSettlement", when an override changed the reply. */
    val overrideKind: String? = null,
    val error: String? = null,
    val request: RemoteMessage? = null,
    val response: RemoteMessage? = null,
)

@Serializable
data class RemoteLog(
    val id: String,
    val createdAt: String,
    val level: String,
    val category: String,
    val message: String,
    val details: String? = null,
    val tid: String? = null,
    val mid: String? = null,
    val remote: String? = null,
    val connectionId: String? = null,
    val transactionId: String? = null,
)

@Serializable
data class RemoteOverride(
    val id: String,
    val tid: String,
    /** "general" (decline, no response, delay) or "rejectSettlement" (settlement 920000 gets F39 95). */
    val kind: String = "general",
    val responseCode: String? = null,
    val delaySeconds: Int? = null,
    val noResponse: Boolean? = null,
    val setBy: String? = null,
    val note: String? = null,
    val createdAt: String,
    val expiresAt: String,
)

@Serializable
data class OverrideRequest(
    val tid: String,
    val kind: String? = null,
    val responseCode: String? = null,
    val delaySeconds: Int? = null,
    val noResponse: Boolean? = null,
    val setBy: String? = null,
    val note: String? = null,
    val minutes: Int? = null,
)

@Serializable
data class Page<T>(val items: List<T>, val next: String? = null)

@Serializable
data class HealthProfile(val name: String, val hash: String)

@Serializable
data class HealthIso(val address: String, val port: Int, val connections: Int = 0)

@Serializable
data class RemoteHealth(
    val status: String,
    val profile: HealthProfile,
    val iso: HealthIso,
    val store: String,
    val retentionHours: Int = 48,
    val cardDataMasked: Boolean = true,
    val serverTime: String? = null,
)

@Serializable
data class RemoteRuleMatch(val mti: String, val processingCode: String = "*")

@Serializable
data class RemoteRuleRespond(val mti: String? = null, val echo: List<String> = emptyList(), val set: Map<String, String> = emptyMap())

@Serializable
data class RemoteRule(val name: String, val match: RemoteRuleMatch, val respond: RemoteRuleRespond = RemoteRuleRespond())

@Serializable
data class RemoteUnmatched(val responseCode: String? = null)

@Serializable
data class RemoteProfile(
    val name: String,
    val description: String = "",
    val hash: String,
    val transactions: List<RemoteRule> = emptyList(),
    val unmatched: RemoteUnmatched = RemoteUnmatched(),
    val framing: JsonObject? = null,
)

/** The terminals and merchant a tester watches. Blank means "any". */
data class TerminalFilter(val tid: String = "", val mid: String = "") {
    val isEmpty: Boolean get() = tid.isBlank() && mid.isBlank()
}

// ---------- saved remote hosts ----------

@Serializable
data class SavedRemoteHost(val id: String, val name: String, val url: String)

@Serializable
data class RemoteHostSettings(
    val hosts: List<SavedRemoteHost> = emptyList(),
    val selectedId: String? = null,
    /** Shown on overrides this tester sets, so others know whose they are. */
    val myName: String = "",
    val lastTid: String = "",
    val lastMid: String = "",
    /** Saved terminal filters, so a tester can switch between their terminals with one click. */
    val presets: List<FilterPreset> = emptyList(),
)

/** A named TID and MID pair. Either may be blank, meaning "any". */
@Serializable
data class FilterPreset(val name: String, val tid: String = "", val mid: String = "") {
    fun toFilter(): TerminalFilter = TerminalFilter(tid, mid)
}

/** Saved in ~/.iso8583studio/remote_hosts.json, beside the app's other settings. */
object RemoteHostStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val file: File get() = File(StudioPaths.home, "remote_hosts.json")

    fun load(): RemoteHostSettings = try {
        if (file.isFile) json.decodeFromString(RemoteHostSettings.serializer(), file.readText()) else RemoteHostSettings()
    } catch (e: Exception) {
        RemoteHostSettings()
    }

    fun save(settings: RemoteHostSettings) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(RemoteHostSettings.serializer(), settings))
        } catch (_: Exception) {
            // Losing a saved host list is an inconvenience, not a reason to interrupt testing.
        }
    }
}

/** Accepts "10.0.0.5:8590", "http://10.0.0.5:8590" or ".../api", and returns "http://host:port". */
fun normalizeBaseUrl(input: String): String {
    var url = input.trim().trimEnd('/')
    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
    if (url.endsWith("/api")) url = url.removeSuffix("/api")
    return url
}
