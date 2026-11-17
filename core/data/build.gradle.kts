plugins {
    alias(libs.plugins.reliquary.kmp.library)
    alias(libs.plugins.reliquary.kmp.hilt)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:network"))
            implementation(project(":core:database"))
            api(libs.androidx.paging.common)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.androidx.paging.runtime)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.android)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.java.keyring)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.okhttp.mockwebserver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.paging.testing)
        }
    }
}
