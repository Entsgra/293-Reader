import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
	override fun apply(target: Project) = with(target) {
		pluginManager.apply("com.android.application")
		extensions.configure<ApplicationExtension> {
			compileSdk = libs.int("compileSdk")
			defaultConfig {
				minSdk = libs.int("minSdk")
				targetSdk = libs.int("targetSdk")
				testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
			}
			compileOptions {
				sourceCompatibility = JAVA_VERSION
				targetCompatibility = JAVA_VERSION
			}
		}
		configureKotlin()
	}
}
