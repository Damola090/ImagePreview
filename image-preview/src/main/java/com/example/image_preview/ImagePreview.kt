//package com.example.image_preview
//
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.aspectRatio
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.draw.shadow
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.painter.Painter
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//
//
//@Composable
//fun ImagePreview(
//    image: Painter,
//    modifier: Modifier = Modifier,
//    description: String = "",
//    contentDescription: String = "",
//    onImageClick: () -> Unit = {}
//) {
//    Box(
//        modifier = modifier
//            .aspectRatio(1f)
//            .clip(RoundedCornerShape(10.dp))
//            .shadow(15.dp, RoundedCornerShape(15.dp))
//            .clickable { onImageClick() }
//    ) {
//        Image(
//            painter = image,
//            contentDescription = contentDescription,
//            contentScale = ContentScale.Crop,
//            modifier = Modifier
//                .fillMaxSize()
//        )
//        Text(
//            text = description,
//            style = MaterialTheme.typography.displayLarge,
//            color = Color.White,
//            overflow = TextOverflow.Ellipsis,
//            maxLines = 1,
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(
//                    Brush.verticalGradient(
//                        listOf(
//                            Color.Transparent,
//                            Color.Black
//                        )
//                    )
//                )
//                .align(Alignment.BottomStart)
//                .padding(8.dp)
//        )
//    }
//}


package com.example.image_preview

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.image_preview.api.KadaraAnalytics
//import com.kadara.analytics.api.KadaraAnalytics
import com.example.image_preview.api.enableDebugLogging

// ─── Application ─────────────────────────────────────────────────────────────

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // This is exactly what a developer using your SDK writes
        KadaraAnalytics.setup(this) {
            apiKey = "test_api_key_123"
            baseUrl = "https://api.your-analytics.com/"
            flushAt = 5                          // flush every 5 events (low for testing)
            trackScreenViews = true
            trackAppLifecycle = true
            superProperties = mapOf(             // attached to EVERY event automatically
                "app_env" to "development",
                "experiment_group" to "sandbox"
            )
            enableDebugLogging()                 // logs every event to Logcat
        }
    }
}

// ─── Debug Panel Activity ─────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AnalyticsDebugPanel()
            }
        }
    }
}

@Composable
fun AnalyticsDebugPanel() {
    val pendingCount by KadaraAnalytics.observePendingCount().collectAsState(initial = 0)
    val capturedEvents = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "KadaraAnalytics Debug Panel",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        // Pending events counter
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Events pending delivery", fontWeight = FontWeight.Medium)
                Text(
                    pendingCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Text("Fire Test Events", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val testEvents = listOf(
            "button_clicked" to mapOf("button" to "sign_up"),
            "page_viewed" to mapOf("page" to "home"),
            "purchase_completed" to mapOf("amount" to 9.99, "currency" to "USD"),
            "video_played" to mapOf("video_id" to "abc123", "duration" to 120),
        )

        testEvents.forEach { (event, props) ->
            Button(
                onClick = {
                    KadaraAnalytics.capture(event, props)
                    capturedEvents.add(0, "✓ $event")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(event)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Identify user
            OutlinedButton(
                onClick = {
                    KadaraAnalytics.identify(
                        userId = "user_456",
                        traits = mapOf("plan" to "pro", "name" to "Test User")
                    )
                    capturedEvents.add(0, "👤 identify: user_456")
                },
                modifier = Modifier.weight(1f)
            ) { Text("Identify") }

            // Manual flush
            OutlinedButton(
                onClick = {
                    KadaraAnalytics.flush()
                    capturedEvents.add(0, "🚀 Manual flush triggered")
                },
                modifier = Modifier.weight(1f)
            ) { Text("Flush Now") }
        }

        Spacer(Modifier.height(16.dp))

        // Event log
        Text("Event Log", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(capturedEvents) { event ->
                Text(
                    event,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                HorizontalDivider()
            }
        }
    }
}
