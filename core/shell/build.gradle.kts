plugins {
    alias(libs.plugins.reliquary.kmp.library)
    alias(libs.plugins.reliquary.kmp.compose)
    alias(libs.plugins.reliquary.kmp.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// What the phone and desktop shells share: the navigation keys and tabs, and the shell's ViewModels.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data"))
            api(project(":core:ui"))
            api(libs.cmp.navigation3.ui)
        }
    }
}
