import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A Kotlin Multiplatform library for Android and the desktop app (#2). Both
 * targets run on the JVM, so commonMain can use JVM libraries (OkHttp, java.time,
 * javax.inject). Tests go in commonTest or desktopTest and run on the desktop JVM,
 * so they need neither an emulator nor Robolectric.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        // AGP 9's multiplatform library plugin; com.android.library can't share a module with KMP.
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                namespace = defaultNamespace
                compileSdk = libs.versionInt("compileSdk")
                minSdk = libs.versionInt("minSdk")
            }
            jvm("desktop")

            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.lib("kotlin-test"))
            }
            sourceSets.getByName("desktopTest").dependencies {
                implementation(libs.lib("junit"))
            }
        }
        configureKotlin()
    }
}
