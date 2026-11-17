plugins {
    alias(libs.plugins.reliquary.kmp.library)
    alias(libs.plugins.reliquary.kmp.hilt)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.okhttp.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.retrofit.core)
            implementation(libs.retrofit.kotlinx.serialization)
            implementation(libs.okhttp.logging)
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.okhttp.mockwebserver)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
