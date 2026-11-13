plugins {
    alias(libs.plugins.reliquary.android.library)
    alias(libs.plugins.reliquary.hilt)
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:download"))
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
