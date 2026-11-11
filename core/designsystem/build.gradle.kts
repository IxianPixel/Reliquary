plugins {
    alias(libs.plugins.reliquary.android.library)
    alias(libs.plugins.reliquary.android.compose)
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui.graphics)
}
