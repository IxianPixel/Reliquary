plugins {
    alias(libs.plugins.reliquary.kmp.library)
    alias(libs.plugins.reliquary.kmp.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    // Exported schemas are committed so later versions can be migrated and tested.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.androidx.room.paging)
            api(libs.androidx.paging.common)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        // Android keeps the framework's SQLite; the JVM has none, so desktop bundles one.
        getByName("desktopMain").dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.androidx.room.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}
