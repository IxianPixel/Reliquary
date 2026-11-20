import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A multiplatform feature module: shared screens and ViewModels in commonMain,
 * built through core:ui's ViewModelGraph (Hilt multibindings in androidMain, the
 * hand-wired graph on desktop).
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("reliquary.kmp.library")
        pluginManager.apply("reliquary.kmp.compose")
        pluginManager.apply("reliquary.kmp.hilt")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(project(":core:designsystem"))
                implementation(project(":core:model"))
                implementation(project(":core:data"))
                implementation(project(":core:ui"))
                implementation(libs.lib("cmp-lifecycle-viewmodel-compose"))
                implementation(libs.lib("cmp-lifecycle-runtime-compose"))
                implementation(libs.lib("androidx-paging-compose"))
            }
        }
    }
}
