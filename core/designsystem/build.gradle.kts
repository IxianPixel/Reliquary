plugins {
    alias(libs.plugins.reliquary.kmp.library)
    alias(libs.plugins.reliquary.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.cmp.material3)
            api(libs.cmp.ui)
            api(libs.cmp.components.resources) // ReliquaryIcons are DrawableResources
        }
    }
}

// Features read ReliquaryIcons' drawables.
compose.resources {
    publicResClass = true
}
