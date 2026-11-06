package eotaek.juyulow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eotaek.juyulow.core.design.theme.JUYULOWTheme
import eotaek.juyulow.feature.comparison.ComparisonRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JUYULOWTheme {
                ComparisonRoute()
            }
        }
    }
}
