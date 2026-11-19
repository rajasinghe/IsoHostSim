package lk.cba.isohostsim.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import lk.cba.isohostsim.ui.screens.HomeScreenViewModel
import lk.cba.isohostsim.ui.screens.landing.HomeScreen
import lk.cba.isohostsim.ui.shell.AppSidebar
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import lk.cba.isohostsim.data.ResultDialogInterface
import lk.cba.isohostsim.data.model.GatewayConfig
import lk.cba.isohostsim.domain.service.ecr.EcrSimulatorService
import lk.cba.isohostsim.ui.navigation.rememberNavigationController
import lk.cba.isohostsim.ui.navigation.stateConfigs.ecr.EcrSimulatorConfig
import lk.cba.isohostsim.ui.screens.ecrSimulator.EcrSimulatorScreen
import lk.cba.isohostsim.ui.screens.generic.MessageParserScreen
import lk.cba.isohostsim.ui.screens.hostSimulator.HostQaBar
import lk.cba.isohostsim.ui.screens.logcat.LogcatViewerScreen
import lk.cba.isohostsim.ui.screens.parser.IsoParserScreen

/**
 * Renders the redesigned screens to PNG files so they can be looked at without starting the app.
 * It does nothing unless the `DESIGN_PREVIEW_DIR` environment variable names a folder:
 *
 *     DESIGN_PREVIEW_DIR=/tmp/preview ./gradlew :composeApp:desktopTest --tests '*ScreenPreviewTest*'
 */
@OptIn(ExperimentalTestApi::class)
class ScreenPreviewTest {
    private val outDir: File? = System.getenv("DESIGN_PREVIEW_DIR")?.takeIf { it.isNotBlank() }?.let(::File)

    @Test
    fun home() = render("home") {
        Shell { HomeScreen(viewModel = HomeScreenViewModel(), onToolSelected = {}) }
    }

    @Test
    fun ecr() = render("ecr") {
        val service = remember {
            EcrSimulatorService(EcrSimulatorConfig(id = "ecr-preview", name = "HNB IM30"))
        }
        Shell { EcrSimulatorScreen(service = service, onBack = {}, onSaveConfig = {}) }
    }

    @Test
    fun ecrWide() = render("ecr-wide", width = 1920, height = 1000) {
        val service = remember {
            EcrSimulatorService(EcrSimulatorConfig(id = "ecr-preview-wide", name = "HNB IM30"))
        }
        Shell { EcrSimulatorScreen(service = service, onBack = {}, onSaveConfig = {}) }
    }

    @Test
    fun homeWide() = render("home-wide", width = 1920, height = 1000) {
        Shell { HomeScreen(viewModel = HomeScreenViewModel(), onToolSelected = {}) }
    }

    @Test
    fun parser() = render("parser") {
        Shell {
            Navigator(object : Screen {
                override val key = "parser-preview"

                @Composable
                override fun Content() {
                    MessageParserScreen(
                        navigationController = rememberNavigationController(LocalNavigator.currentOrThrow),
                        onError = object : ResultDialogInterface {
                            override fun onError(item: @Composable () -> Unit) {}
                            override fun onSuccess(item: @Composable () -> Unit) {}
                        },
                        onBack = {},
                        hostConfigs = listOf(GatewayConfig(id = "preview", name = "gimhan-sim")),
                    )
                }
            })
        }
    }

    @Test
    fun isoParser() = render("iso-parser") {
        Shell { IsoParserScreen(hostConfigs = listOf(GatewayConfig(id = "preview", name = "gimhan-sim"))) }
    }

    @Test
    fun logcat() = render("logcat") {
        Shell { LogcatViewerScreen(onBack = {}) }
    }

    @Test
    fun hostQaBar() = render("host-qa") {
        Shell {
            Column(Modifier.fillMaxSize()) {
                HostQaBar(config = GatewayConfig(id = "preview-host", name = "gimhan-sim"), onSaveClick = {})
            }
        }
    }

    @Composable
    private fun Shell(content: @Composable () -> Unit) {
        Row(Modifier.fillMaxSize()) {
            AppSidebar(
                onHome = {}, onOpenTool = {}, onExportConfig = {}, onImportConfig = {},
                onSettings = {}, onAbout = {}, onExit = {},
            )
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
    }

    private fun render(name: String, width: Int = 1280, height: Int = 800, content: @Composable () -> Unit) {
        val dir = outDir ?: return
        dir.mkdirs()
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = width, height = height) {
                setContent { AppTheme(darkTheme = dark) { content() } }
                waitForIdle()
                val file = File(dir, "$name-${if (dark) "dark" else "light"}.png")
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
            }
        }
    }
}
