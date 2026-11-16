import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Compose Multiplatform for a [KmpLibraryConventionPlugin] module. Apply after it.
 * On Android the JetBrains artifacts resolve to the Jetpack ones, so the app keeps
 * the versions it pins (material3 for the Expressive APIs).
 *
 * Strings and icons are Compose resources in `commonMain/composeResources`, with
 * the `Res` class in `<namespace>.resources`. Modules whose resources other modules
 * use (core:designsystem, core:ui) set `compose.resources { publicResClass = true }`.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        extensions.configure<ComposeExtension> {
            extensions.configure<ResourcesExtension> {
                packageOfResClass = "$defaultNamespace.resources"
                generateResClass = ResourcesExtension.ResourceClassGeneration.Always
            }
        }

        extensions.configure<KotlinMultiplatformExtension> {
            // The AGP 9 multiplatform library plugin leaves Android resources off; Compose resources need them.
            targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                androidResources.enable = true
            }
            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.lib("cmp-runtime"))
                implementation(libs.lib("cmp-foundation"))
                implementation(libs.lib("cmp-ui"))
                implementation(libs.lib("cmp-material3"))
                implementation(libs.lib("cmp-components-resources"))
                implementation(libs.lib("cmp-ui-tooling-preview"))
            }
            // Compose resources ask Skiko for the system theme, so desktop tests need the native runtime.
            sourceSets.getByName("desktopTest").dependencies {
                implementation(ComposePlugin.DesktopDependencies.currentOs)
            }
            sourceSets.getByName("androidMain").dependencies {
                implementation(project.dependencies.platform(libs.lib("androidx-compose-bom")))
                implementation(libs.lib("androidx-compose-material3"))
            }
        }
    }
}
