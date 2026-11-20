plugins {
    alias(libs.plugins.reliquary.kmp.library)
    alias(libs.plugins.reliquary.kmp.compose)
}

// Library UI shared by the feature screens: video, channel and playlist rows,
// thumbnails, loading, error and empty states, and how screens get ViewModels.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:designsystem"))
            api(project(":core:model"))
            implementation(project(":core:data"))
            api(libs.androidx.paging.compose)
            api(libs.cmp.lifecycle.viewmodel.compose) // injectedViewModel is inline
            api(libs.cmp.material3.adaptive) // window size classes: PaneScaffoldDirective (#11)
            api(libs.cmp.material3.adaptive.layout) // calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth (#11)
            implementation(libs.cmp.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose) // permission requests
        }
    }
}

// Features use these strings too.
compose.resources {
    publicResClass = true
}
