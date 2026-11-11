plugins {
    alias(libs.plugins.reliquary.android.library)
    alias(libs.plugins.reliquary.android.compose)
}

// Library UI shared by the feature screens: video, channel and playlist rows,
// thumbnails, and loading, error and empty states.
dependencies {
    api(project(":core:designsystem"))
    api(project(":core:model"))
    implementation(project(":core:data"))
    api(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
}
