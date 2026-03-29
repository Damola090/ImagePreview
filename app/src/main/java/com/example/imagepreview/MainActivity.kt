package com.example.imagepreview

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
//import com.example.image_preview.ImagePreview
//import com.example.image_preview.ImagePreview
import com.example.image_preview.AnalyticsDebugPanel
import com.example.image_preview.api.KadaraAnalytics
import com.example.image_preview.api.enableDebugLogging
import com.example.imagepreview.ui.theme.ImagePreviewTheme

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


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImagePreviewTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    ImagePreview()
//                    ImagePreview
//                    ImagePreview(
//                        image  = painterResource(id = R.drawable.wine_glass),
//                    )
                    AnalyticsDebugPanel()
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ImagePreviewTheme {
        Greeting("Android")
    }
}