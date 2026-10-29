import com.android.build.api.dsl.LibraryExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.withType

/**
 * Roborazzi screenshot tests on the JVM (Robolectric), for the README. Apply to a feature module;
 * its screenshot tests live in its unit test source set, so they reach its internal composables.
 * Every module records into `docs/screenshots/`: `./gradlew recordRoborazziDebug`.
 */
class AndroidScreenshotsConventionPlugin : Plugin<Project> {
	override fun apply(target: Project) = with(target) {
		pluginManager.apply("io.github.takahirom.roborazzi")
		extensions.configure<LibraryExtension> {
			testOptions.unitTests.isIncludeAndroidResources = true
		}
		extensions.configure<RoborazziExtension> {
			outputDir.set(rootProject.layout.projectDirectory.dir("docs/screenshots"))
		}
		tasks.withType<Test>().configureEach {
			// Robolectric reaches into FileDescriptor internals, and loads its native graphics.
			jvmArgs(
				"--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
				"--enable-native-access=ALL-UNNAMED",
			)
		}
		dependencies {
			add("testImplementation", project(":core:screenshots"))
			add("testImplementation", libs.lib("androidx-compose-ui-test-manifest"))
		}
	}
}
