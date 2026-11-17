import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.reliquary.jvm.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// One version for the phone and desktop apps; scripts/prepare-release.py bumps it in app/build.gradle.kts.
val appVersion: String = Regex("""versionName = "([^"]+)"""")
    .find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(project(":core:download"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:shell"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:channels"))
    implementation(project(":feature:playlists"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:queue"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.material3)
    implementation(libs.cmp.material3.adaptive.navigation.suite)
    implementation(libs.cmp.components.resources)
    implementation(libs.cmp.lifecycle.runtime.compose)
    implementation(libs.cmp.lifecycle.viewmodel.compose)
    implementation(libs.cmp.lifecycle.viewmodel.navigation3)
    implementation(libs.cmp.navigation3.ui)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.javax.inject)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
}

// Logs request lines (credentials redacted) when run from Gradle; packaged builds don't.
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    systemProperty("reliquary.debug", "true")
}

compose.desktop {
    application {
        mainClass = "com.malgra.reliquary.desktop.MainKt"
        // Packaging needs jpackage, which Android Studio's bundled JDK lacks: use a JDK 21 toolchain when one is installed.
        runCatching {
            javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }.get()
        }.onSuccess { javaHome = it.metadata.installationPath.asFile.absolutePath }
        // The bundled SQLite loads a native library.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            // macOS ships as a zipped .app (packageMacZip) until 1.0: jpackage refuses a DMG version starting with 0.
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Reliquary"
            packageVersion = appVersion
            description = "Tube Archivist client"
            vendor = "Reliquary"
            licenseFile.set(rootProject.file("LICENSE"))
            // TLS needs jdk.crypto.ec, the player's loopback proxy jdk.httpserver; the rest are what OkHttp, Room and DataStore use.
            modules("java.logging", "java.naming", "java.sql", "jdk.crypto.ec", "jdk.httpserver", "jdk.unsupported")

            macOS {
                bundleID = "com.malgra.reliquary"
                // jpackage refuses a first number of 0 even for an .app; setMacBundleVersion writes the real one.
                if (appVersion.startsWith("0.")) packageVersion = "1.0.0"
                iconFile.set(project.file("icons/icon.icns"))
                infoPlist {
                    // macOS 15 asks before an app reaches the local network, where many TA servers live.
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>Reliquary connects to your Tube Archivist server, which may be on your local network.</string>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "Reliquary"
                // Keeps upgrades replacing the installed version instead of installing beside it.
                upgradeUuid = "b5e80117-2502-4ea8-97bb-5a116414e38e"
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
                packageName = "reliquary"
                appCategory = "Video"
            }
        }
    }
}

val setMacBundleVersion by tasks.registering(Exec::class) {
    description = "Writes the app's real version into the macOS bundle's Info.plist."
    dependsOn("createDistributable")
    val plist = layout.buildDirectory.file("compose/binaries/main/app/Reliquary.app/Contents/Info.plist")
    commandLine(
        "/usr/libexec/PlistBuddy",
        "-c", "Set :CFBundleShortVersionString $appVersion",
        "-c", "Set :CFBundleVersion $appVersion",
        plist.get().asFile.absolutePath,
    )
}

val packageMacZip by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Zips the macOS app bundle."
    dependsOn(setMacBundleVersion)
    val app = layout.buildDirectory.file("compose/binaries/main/app/Reliquary.app")
    val zip = layout.buildDirectory.file("compose/binaries/main/zip/Reliquary-$appVersion-macos.zip")
    outputs.file(zip)
    doFirst { zip.get().asFile.parentFile.mkdirs() }
    // ditto, as Finder's Compress does: keeps the launcher's executable bit and the runtime's symlinks.
    commandLine("ditto", "-c", "-k", "--keepParent", app.get().asFile.absolutePath, zip.get().asFile.absolutePath)
}

// The player's Linux backend links against GStreamer, which jpackage can't see; the Compose plugin
// has no option for extra package dependencies, so they're added to the built .deb.
val addDebDependencies by tasks.registering(Exec::class) {
    description = "Adds GStreamer, which the video player needs on Linux, to the .deb's dependencies."
    onlyIf { System.getProperty("os.name").startsWith("Linux") }
    val debs = layout.buildDirectory.dir("compose/binaries/main/deb")
    commandLine(
        "sh", "-c",
        """
        set -e
        deps="gstreamer1.0-plugins-base, gstreamer1.0-plugins-good, gstreamer1.0-libav"
        for deb in "${'$'}1"/*.deb; do
            work=${'$'}(mktemp -d)
            dpkg-deb -R "${'$'}deb" "${'$'}work"
            control="${'$'}work/DEBIAN/control"
            if grep -q '^Depends:' "${'$'}control"; then
                sed -i "s/^Depends: .*/&, ${'$'}deps/" "${'$'}control"
            else
                echo "Depends: ${'$'}deps" >> "${'$'}control"
            fi
            dpkg-deb -b --root-owner-group "${'$'}work" "${'$'}deb"
            rm -rf "${'$'}work"
        done
        """.trimIndent(),
        "sh", debs.get().asFile.absolutePath,
    )
}
tasks.matching { it.name == "packageDeb" }.configureEach { finalizedBy(addDebDependencies) }
