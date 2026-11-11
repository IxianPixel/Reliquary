plugins {
    alias(libs.plugins.reliquary.android.feature)
}

dependencies {
    implementation(project(":core:data"))

    testImplementation(libs.kotlinx.coroutines.test)
}
