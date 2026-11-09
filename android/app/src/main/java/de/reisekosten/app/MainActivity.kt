package de.reisekosten.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                App(vm)
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCEBE7),
    onPrimaryContainer = Color(0xFF042F2E),
    secondaryContainer = Color(0xFFE6E1D6),
    background = Color(0xFFF7F5F0),
    surface = Color(0xFFF7F5F0),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EEE7),
    surfaceContainerHigh = Color(0xFFEBE7DE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF14171A),
    surface = Color(0xFF14171A),
    surfaceContainerLow = Color(0xFF1D2125),
    surfaceContainer = Color(0xFF22272B),
    surfaceContainerHigh = Color(0xFF2A3035),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
