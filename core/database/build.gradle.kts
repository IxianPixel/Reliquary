plugins {
    alias(libs.plugins.reliquary.android.library)
    alias(libs.plugins.reliquary.hilt)
    alias(libs.plugins.room)
}

room {
    // Exported schemas are committed so later versions can be migrated and tested.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
