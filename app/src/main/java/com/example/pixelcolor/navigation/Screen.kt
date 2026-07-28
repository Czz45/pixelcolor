package com.example.pixelcolor.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object PixelPreview : Screen("pixel_preview/{imageUri}") {
        fun create(imageUri: String) = "pixel_preview/$imageUri"
    }
    object Game : Screen("game/{saveId}") {
        fun create(saveId: String) = "game/$saveId"
    }
    object Completion : Screen("completion/{saveId}?preview={preview}") {
        fun create(saveId: String, preview: Boolean = false) = "completion/$saveId?preview=$preview"
    }
    object CrashLogs : Screen("crash_logs")
}
