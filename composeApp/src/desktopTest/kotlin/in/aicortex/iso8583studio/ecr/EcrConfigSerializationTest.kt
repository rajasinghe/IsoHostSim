package `in`.aicortex.iso8583studio.ecr

import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.SimulatorConfigCollection
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import `in`.aicortex.iso8583studio.ui.navigation.stateConfigs.ecr.EcrTransport
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EcrConfigSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun collectionJson(ecrConfigs: String) = """
        {
          "hostConfigs": [], "hsmConfigs": [], "apduConfigs": [], "posConfigs": [],
          "ecrConfigs": $ecrConfigs,
          "atmConfigs": [], "cardConfigs": [], "switchConfigs": [], "acquirerConfigs": [],
          "issuerConfigs": [], "exportedAt": 0, "version": "1.0"
        }
    """.trimIndent()

    @Test
    fun `old files with string ecr entries still load`() {
        val decoded = json.decodeFromString(
            SimulatorConfigCollection.serializer(),
            collectionJson("""["legacy-entry", "another"]"""),
        )
        assertTrue(decoded.ecrConfigs.isEmpty())
    }

    @Test
    fun `ecr configs round trip and a bad entry is skipped`() {
        // Fixed timestamps: a default left unchanged is not encoded, and `currentTimeMillis()`
        // would then differ on decode if both happened in the same millisecond.
        val config = EcrSimulatorConfig(
            id = "ecr-1", name = "IM30 Wi-Fi", transport = EcrTransport.USB,
            createdDate = 1_700_000_000_000, modifiedDate = 1_700_000_000_000,
        )
        val encoded = json.encodeToString(EcrSimulatorConfig.serializer(), config)

        val decoded = json.decodeFromString(
            SimulatorConfigCollection.serializer(),
            collectionJson("""[$encoded, "stale", {"not": "a config"}]"""),
        )

        assertEquals(listOf(config), decoded.ecrConfigs)
    }

    @Test
    fun `a file without the field loads with no ecr configs`() {
        val withoutField = collectionJson("[]").replace("\"ecrConfigs\": [],", "")
        val decoded = json.decodeFromString(SimulatorConfigCollection.serializer(), withoutField)
        assertTrue(decoded.ecrConfigs.isEmpty())
    }
}
