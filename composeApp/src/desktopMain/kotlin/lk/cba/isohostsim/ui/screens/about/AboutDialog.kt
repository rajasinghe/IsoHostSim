package lk.cba.isohostsim.ui.screens.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import lk.cba.isohostsim.StudioVersion
import lk.cba.isohostsim.ui.AccentTeal
import lk.cba.isohostsim.update.UpdateStatusRow //gimhan added
import iso8583studio.composeapp.generated.resources.Res
import iso8583studio.composeapp.generated.resources.app
import iso8583studio.composeapp.generated.resources.compose_multiplatform
import java.awt.Desktop
import java.net.URI
import java.util.*

/**
 * About dialog for ISO8583Studio application
 *
 * @param onCloseRequest Callback to invoke when the dialog should be closed
 * @param version Application version (defaults to BuildConfig.VERSION if available, or "1.0.14")
 */
@Composable
fun AboutDialog(
    onCloseRequest: () -> Unit,
    version: String = StudioVersion
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = "About POS Testing Platform",
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            width = 500.dp,
            height = 600.dp
        ),
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(
                        state = rememberScrollState()
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App logo
                Image(
                    painter = painterResource(Res.drawable.app),
                    contentDescription = "POS Testing Platform Logo",
                    modifier = Modifier
                        .size(120.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // App name and version
                Text(
                    text = "POS Testing Platform",
                    style = MaterialTheme.typography.h4,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Version $version",
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Update check //gimhan added
                UpdateStatusRow(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))

                // App description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ISO8583 Message Processing Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "A professional desktop application for viewing, editing, " +
                                    "testing, and simulating ISO8583 financial messages. " +
                                    "Designed for developers, testers, and system integrators " +
                                    "working with payment processing systems.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Features
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "Key Features",
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FeatureItem(
                        icon = Icons.Default.CompareArrows,
                        text = "Message testing and simulation"
                    )

                    FeatureItem(
                        icon = Icons.Default.Settings,
                        text = "Flexible gateway configuration"
                    )

                    FeatureItem(
                        icon = Icons.Default.MonitorHeart,
                        text = "Real-time transaction monitoring"
                    )

                    FeatureItem(
                        icon = Icons.Default.Router,
                        text = "Host simulation capabilities"
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Licence and credits
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Internal QA build",
                        style = MaterialTheme.typography.subtitle2
                    )

                    Text(
                        "Free software under the GNU AGPL v3.",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                    )

                    Text(
                        "The ECR Simulator uses the CBA ECR class library (TCPJAVA_CBA_ClassLibrary).",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Close button
                Button(
                    onClick = onCloseRequest,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentTeal,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.body1
        )
    }
}

/**
 * Helper function to open a web page
 */
private fun openWebpage(uri: String) {
    try {
        Desktop.getDesktop().browse(URI(uri))
    } catch (e: Exception) {
        // Handle exception (could show error dialog)
    }
}

/**
 * Helper function to open email client
 */
private fun openEmailClient(email: String) {
    try {
        Desktop.getDesktop().mail(URI("mailto:$email"))
    } catch (e: Exception) {
        // Handle exception (could show error dialog)
    }
}
