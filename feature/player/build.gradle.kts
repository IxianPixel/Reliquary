plugins {
    alias(libs.plugins.reliquary.kmp.feature)
}

// The player: shared screens and progress/SponsorBlock logic over VideoPlayer,
// media3 behind a MediaSession on Android, ComposeMediaPlayer on desktop.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.datasource.okhttp)
            implementation(libs.androidx.media3.ui) // SubtitleView, which follows the system caption style
            implementation(libs.androidx.media3.ui.compose)
            implementation(libs.androidx.media3.ui.compose.material3)
            implementation(libs.androidx.activity.compose)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.composemediaplayer)
            implementation(libs.okhttp.core)
            implementation(libs.kotlinx.coroutines.swing) // Dispatchers.Main
            implementation(libs.cmp.ui.backhandler) // Escape as Back
        }
        getByName("desktopTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okhttp.mockwebserver)
        }
    }
}
