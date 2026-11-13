import java.util.Properties

plugins {
    alias(libs.plugins.reliquary.android.application)
    alias(libs.plugins.reliquary.android.compose)
    alias(libs.plugins.reliquary.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.malgra.reliquary"

    defaultConfig {
        applicationId = "com.malgra.reliquary"
        // Bumped on the release/* branch (scripts/prepare-release.py). versionCode = major * 10000 + minor * 100 + patch.
        versionCode = 100
        versionName = "0.1.0"
    }

    // Release signing, only when a keystore is given: in CI from secrets, locally from
    // keystore.properties. Without one (as on F-Droid's build server) the release APK is unsigned.
    val signing = releaseSigning()
    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getValue("storeFile"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    // No Google-encrypted dependency report in the APK: F-Droid and IzzyOnDroid reject it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        // Installs next to a release build, labelled "Reliquary Debug" (src/debug/res).
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signing != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:sync"))
    implementation(project(":core:download"))
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
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.media3.session)
}

/**
 * The release keystore: from RELIQUARY_KEYSTORE / _STORE_PASSWORD / _KEY_ALIAS /
 * _KEY_PASSWORD (CI), else from a git-ignored keystore.properties next to this
 * file. Null when neither is set.
 */
fun releaseSigning(): Map<String, String>? {
    val env = System.getenv()
    env["RELIQUARY_KEYSTORE"]?.takeIf { it.isNotBlank() }?.let { store ->
        return mapOf(
            "storeFile" to store,
            "storePassword" to env.getValue("RELIQUARY_STORE_PASSWORD"),
            "keyAlias" to env.getValue("RELIQUARY_KEY_ALIAS"),
            "keyPassword" to env.getValue("RELIQUARY_KEY_PASSWORD"),
        )
    }
    val props = file("keystore.properties").takeIf { it.exists() } ?: return null
    val p = Properties()
    props.inputStream().use { p.load(it) }
    return listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { p.getProperty(it).orEmpty() }
}

