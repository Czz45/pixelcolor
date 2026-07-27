package com.example.pixelcolor

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.pixelcolor.data.SettingsStore
import com.example.pixelcolor.navigation.PixelNavGraph
import com.example.pixelcolor.ui.theme.AllThemes
import com.example.pixelcolor.ui.theme.PixelColorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read the saved theme synchronously from DataStore so the FIRST frame already uses the
        // correct theme. (An unrelated SharedPreferences used to default to index 0 = Dark, then
        // the DataStore flow emitted the real value a frame later → dark→light flash on entry.)
        val settingsStore = SettingsStore(this)
        val initialThemeIndex = runBlocking(Dispatchers.IO) { settingsStore.themeIndex.first() }
        val theme = AllThemes.getOrElse(initialThemeIndex) { AllThemes[0] }
        val r = (theme.bg.red * 255).toInt()
        val g = (theme.bg.green * 255).toInt()
        val b = (theme.bg.blue * 255).toInt()
        val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        window.setBackgroundDrawable(ColorDrawable(argb))

        enableEdgeToEdge()

        window.statusBarColor = argb
        window.navigationBarColor = argb
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !theme.isDark
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !theme.isDark

        setContent {
            val themeIndex by settingsStore.themeIndex.collectAsState(initial = initialThemeIndex)
            PixelColorTheme(themeIndex = themeIndex) { PixelNavGraph() }
        }
    }
}
