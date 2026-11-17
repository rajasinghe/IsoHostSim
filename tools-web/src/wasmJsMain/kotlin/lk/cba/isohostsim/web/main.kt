package lk.cba.isohostsim.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import lk.cba.isohostsim.analytics.Analytics
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Analytics.sink = WebAnalyticsSink
    // The embedding page drives deep links and the browser's back button through `navigate`
    // messages; listen before composing so nothing sent in reply to app_ready is missed.
    WebBridge.listenForParentNavigation()
    ComposeViewport(document.body!!) {
        ToolsWebApp()
    }
    // app_ready is posted by ToolsWebApp once the bundled fonts are loaded and the first frame can draw.
}
