import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Hilt for the Android side of a [KmpLibraryConventionPlugin] module: Hilt modules
 * live in androidMain and only the Android target runs the Hilt compiler. Shared
 * classes use plain javax.inject annotations, which desktop wires up by hand.
 * The Hilt Gradle plugin isn't needed here: only the app has @AndroidEntryPoint classes.
 */
class KmpHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.lib("javax-inject"))
            }
            sourceSets.getByName("androidMain").dependencies {
                implementation(libs.lib("hilt-android"))
            }
        }
        dependencies.add("kspAndroid", libs.lib("hilt-compiler"))
        Unit
    }
}
