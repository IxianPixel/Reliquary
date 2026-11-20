plugins {
    alias(libs.plugins.reliquary.kmp.library)
    alias(libs.plugins.reliquary.kmp.hilt)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:data"))
            implementation(project(":core:download"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.androidx.work.runtime)
            implementation(libs.androidx.hilt.work)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.hilt.compiler)
}
