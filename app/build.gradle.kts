plugins {
    alias(libs.plugins.reliquary.android.application)
    alias(libs.plugins.reliquary.android.compose)
    alias(libs.plugins.reliquary.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.reliquary"

    defaultConfig {
        applicationId = "app.reliquary"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:channels"))
    implementation(project(":feature:playlists"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:player"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
}
