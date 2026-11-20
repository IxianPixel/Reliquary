import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.process.ExecOperations
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.reliquary.jvm.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// One version for the phone and desktop apps; scripts/prepare-release.py bumps it in app/build.gradle.kts.
val appVersion: String = Regex("""versionName = "([^"]+)"""")
    .find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]

// libVLC for the player: pinned in vlc.properties, bundled on macOS and Windows, the system's on Linux.
val vlc = Properties().apply { file("vlc.properties").inputStream().use(::load) }
val vlcVersion: String = vlc.getProperty("version")
val vlcTarget: String? = System.getProperty("os.name").lowercase().let { os ->
    when {
        os.startsWith("mac") -> if (System.getProperty("os.arch") == "aarch64") "macos-arm64" else "macos-x64"
        os.startsWith("windows") -> "windows-x64"
        else -> null
    }
}
val vlcCache = File(gradle.gradleUserHomeDir, "caches/reliquary-vlc/$vlcVersion")
// Compose copies <root>/<os>-<arch>/ into the app's resources: vlc/ ends up at <resources>/vlc.
val vlcResources = layout.buildDirectory.dir("vlc-resources")

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
    implementation(project(":feature:player"))
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
            if (vlcTarget != null) appResourcesRootDir.set(vlcResources)

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
                        <key>NSAppTransportSecurity</key>
                        <dict>
                            <!-- The player streams through a loopback proxy at http://127.0.0.1. -->
                            <key>NSAllowsLocalNetworking</key>
                            <true/>
                        </dict>
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

val setMacBundleVersion = tasks.register<Exec>("setMacBundleVersion") {
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

// Writing the version edits Info.plist after jpackage signed the bundle, which breaks the seal: Gatekeeper then
// reports an invalid signature, where a consistent ad hoc one gets the usual "unidentified developer" prompt.
// No hardened runtime, so libVLC's plugins load without the library-validation exception they'd need.
val signMacApp = tasks.register<Exec>("signMacApp") {
    description = "Re-seals the macOS app bundle ad hoc after its version is written."
    dependsOn(setMacBundleVersion)
    val app = layout.buildDirectory.dir("compose/binaries/main/app/Reliquary.app")
    commandLine("codesign", "--force", "--sign", "-", app.get().asFile.absolutePath)
}

val packageMacZip = tasks.register<Exec>("packageMacZip") {
    group = "compose desktop"
    description = "Zips the macOS app bundle."
    dependsOn(signMacApp)
    val app = layout.buildDirectory.file("compose/binaries/main/app/Reliquary.app")
    val zip = layout.buildDirectory.file("compose/binaries/main/zip/Reliquary-$appVersion-macos.zip")
    outputs.file(zip)
    doFirst { zip.get().asFile.parentFile.mkdirs() }
    // ditto, as Finder's Compress does: keeps the launcher's executable bit and the runtime's symlinks.
    commandLine("ditto", "-c", "-k", "--keepParent", app.get().asFile.absolutePath, zip.get().asFile.absolutePath)
}

// On Linux the player uses the system's libVLC, which jpackage can't see; the Compose plugin
// has no option for extra package dependencies, so they're added to the built .deb.
val addDebDependencies = tasks.register<Exec>("addDebDependencies") {
    description = "Adds libVLC, which the video player needs on Linux, to the .deb's dependencies."
    onlyIf { System.getProperty("os.name").startsWith("Linux") }
    val debs = layout.buildDirectory.dir("compose/binaries/main/deb")
    commandLine(
        "sh", "-c",
        """
        set -e
        deps="libvlc5, vlc-plugin-base, vlc-plugin-video-output"
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

// For distributions without .deb: the app image as it is, plus a README naming the VLC packages it needs.
// GNU tar, as ditto for the Mac zip: the image goes in exactly as jpackage wrote it, owned by root rather than the builder.
val packageLinuxTarGz = tasks.register<Exec>("packageLinuxTarGz") {
    group = "compose desktop"
    description = "Archives the Linux app image as a portable .tar.gz."
    dependsOn("createDistributable")
    onlyIf { System.getProperty("os.name").startsWith("Linux") }
    val app = layout.buildDirectory.dir("compose/binaries/main/app")
    val readme = file("linux/README.txt")
    val archive = layout.buildDirectory.file("compose/binaries/main/tar/Reliquary-$appVersion-linux-x64.tar.gz")
    inputs.file(readme)
    outputs.file(archive)
    doFirst { archive.get().asFile.parentFile.mkdirs() }
    commandLine(
        "tar", "-czf", archive.get().asFile.absolutePath, "--owner=0", "--group=0",
        "-C", app.get().asFile.absolutePath, "Reliquary",
        "-C", readme.parentFile.absolutePath, "--transform", "s,^README.txt,Reliquary/README.txt,", "README.txt",
    )
}

/** Downloads [url] into [target] once, and fails unless its SHA-256 is [sha256]. */
abstract class FetchVerified : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputFile abstract val target: RegularFileProperty

    @TaskAction
    fun fetch() {
        val file = target.get().asFile
        if (file.exists() && sha256Of(file) == sha256.get()) return
        file.parentFile.mkdirs()
        val part = File(file.path + ".part")
        URI(url.get()).toURL().openStream().use { input -> Files.copy(input, part.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        val actual = sha256Of(part)
        if (actual != sha256.get()) {
            part.delete()
            throw GradleException("${url.get()} has SHA-256 $actual, not the pinned ${sha256.get()} (vlc.properties).")
        }
        Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}

// The source offer the LGPL and GPL ask for, attached to every desktop release whatever OS it was built on.
tasks.register<FetchVerified>("fetchVlcSource") {
    description = "Downloads the VLC source tarball that vlc.properties pins, to attach to the release."
    url.set(vlc.getProperty("source.url"))
    sha256.set(vlc.getProperty("source.sha256"))
    target.set(layout.buildDirectory.file("vlc-source/${vlc.getProperty("source.url").substringAfterLast('/')}"))
}

/** Copies libvlc, libvlccore (as plain files, not VLC.app's symlinks) and plugins/ out of VLC's macOS disk image. */
abstract class ExtractVlcFromDmg : DefaultTask() {
    @get:InputFile abstract val dmg: RegularFileProperty
    @get:OutputDirectory abstract val into: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun extract() {
        val out = into.get().asFile.apply { deleteRecursively(); mkdirs() }
        val mount = Files.createTempDirectory("vlc-dmg").toFile()
        exec.exec {
            commandLine("hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen", "-mountpoint", mount.path, dmg.get().asFile.path)
            standardOutput = OutputStream.nullOutputStream()
        }
        try {
            val macos = File(mount, "VLC.app/Contents/MacOS")
            for (name in listOf("libvlc.dylib", "libvlccore.dylib")) {
                // Their install names are @rpath/libvlc.dylib and @rpath/libvlccore.dylib, so keep these names.
                Files.copy(File(macos, "lib/$name").toPath(), File(out, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            exec.exec { commandLine("ditto", File(macos, "plugins").path, File(out, "plugins").path) }
        } finally {
            exec.exec { commandLine("hdiutil", "detach", mount.path, "-quiet") }
            mount.delete()
        }
    }
}

if (vlcTarget != null) {
    // Only the plugins the player needs are bundled: vlc-plugins.txt lists them, one per line, optionally prefixed by the platform.
    val platform = vlcTarget.substringBefore('-')
    val vlcPluginNames: List<String> = file("vlc-plugins.txt").readLines().map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .filter { !it.contains(':') || it.substringBefore(':') == platform }
        .map { it.substringAfter(':') }
    val fetchVlc = tasks.register<FetchVerified>("fetchVlc") {
        description = "Downloads the pinned libVLC for this OS and checks its SHA-256."
        url.set(vlc.getProperty("$vlcTarget.url"))
        sha256.set(vlc.getProperty("$vlcTarget.sha256"))
        target.set(File(vlcCache, vlc.getProperty("$vlcTarget.url").substringAfterLast('/')))
    }
    val vlcFiles: Any = if (vlcTarget.startsWith("macos")) {
        tasks.register<ExtractVlcFromDmg>("extractVlc") {
            dmg.set(fetchVlc.flatMap { it.target })
            into.set(layout.buildDirectory.dir("vlc-extracted/$vlcTarget"))
        }
    } else {
        // The win64 zip holds vlc-<version>/libvlc.dll, libvlccore.dll and plugins/.
        zipTree(fetchVlc.flatMap { it.target }).matching {
            include("vlc-$vlcVersion/libvlc.dll", "vlc-$vlcVersion/libvlccore.dll", "vlc-$vlcVersion/plugins/**")
        }
    }
    val prepareVlc = tasks.register<Sync>("prepareVlc") {
        description = "Puts libVLC, its plugins and its licences where the app's resources are built from."
        val pluginSuffix = if (vlcTarget == "windows-x64") "_plugin.dll" else "_plugin.dylib"
        val libraries = if (vlcTarget == "windows-x64") listOf("libvlc.dll", "libvlccore.dll") else listOf("libvlc.dylib", "libvlccore.dylib")
        // zipTree() of fetchVlc's output doesn't carry the task, so Windows would read a zip nothing had downloaded yet.
        dependsOn(fetchVlc)
        from(vlcFiles) {
            include(libraries.map { "**/$it" } + vlcPluginNames.map { "**/lib$it$pluginSuffix" })
            if (vlcTarget == "windows-x64") eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        }
        from("vlc-licences")
        from(resources.text.fromString(
            """
            libVLC $vlcVersion and its plugins, from VideoLAN (https://www.videolan.org/vlc/).
            libVLC is licensed under the GNU LGPL 2.1 or later (COPYING.LIB); some plugins under the GNU GPL 2 or later (COPYING).
            Source code: ${vlc.getProperty("source.url")}
            It is also attached to every Reliquary release that ships a desktop build.
            """.trimIndent() + "\n",
        ).asFile()) { rename { "README.txt" } }
        includeEmptyDirs = false
        into(vlcResources.map { it.dir("$vlcTarget/vlc") })
        // A misspelt name would otherwise leave its decoder out without a word.
        val expected = vlcPluginNames.map { "lib$it$pluginSuffix" }
        val version = vlcVersion
        doLast {
            val copied = destinationDir.walk().map { it.name }.toSet()
            val missing = expected.filter { it !in copied }
            if (missing.isNotEmpty()) throw GradleException("vlc-plugins.txt names plugins libVLC $version doesn't have: $missing")
        }
    }
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareVlc) }
}
