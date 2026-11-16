package `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * Reads `ecrConfigs` from saved-config files of any age.
 *
 * Before the ECR Simulator existed the field was declared as a list of strings, so older files
 * may hold strings there. Those entries (and any malformed object) are skipped instead of failing
 * the whole file, which would otherwise wipe every saved gateway on load.
 */
object LenientEcrConfigListSerializer : KSerializer<List<EcrSimulatorConfig>> {

    private val delegate = ListSerializer(EcrSimulatorConfig.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<EcrSimulatorConfig>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<EcrSimulatorConfig> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = input.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.filterIsInstance<JsonObject>().mapNotNull { element ->
            runCatching {
                input.json.decodeFromJsonElement(EcrSimulatorConfig.serializer(), element)
            }.getOrNull()
        }
    }
}
