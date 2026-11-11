import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // AGP 9 compiles Kotlin itself; the kotlin-android plugin must not be applied.
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.versionInt("compileSdk")
            defaultConfig {
                minSdk = libs.versionInt("minSdk")
                targetSdk = libs.versionInt("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        configureKotlin()
        addUnitTestDependencies()
    }
}
