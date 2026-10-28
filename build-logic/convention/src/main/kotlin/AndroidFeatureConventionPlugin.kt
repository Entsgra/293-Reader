import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

class AndroidFeatureConventionPlugin : Plugin<Project> {
	override fun apply(target: Project) = with(target) {
		pluginManager.apply("reader.android.library")
		pluginManager.apply("reader.android.compose")
		pluginManager.apply("reader.hilt")
		pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
		dependencies {
			add("implementation", project(":core:designsystem"))
			add("implementation", project(":core:model"))
			add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
			add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
			add("implementation", libs.lib("androidx-navigation-compose"))
			add("implementation", libs.lib("androidx-hilt-navigation-compose"))
			add("implementation", libs.lib("kotlinx-serialization-core"))
			add("testImplementation", libs.lib("kotlinx-coroutines-test"))
			add("testImplementation", libs.lib("turbine"))
		}
	}
}
