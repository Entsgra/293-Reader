import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
	get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
	findLibrary(alias).get()

internal fun VersionCatalog.int(alias: String): Int =
	findVersion(alias).get().requiredVersion.toInt()

internal val JAVA_VERSION = JavaVersion.VERSION_17

/** `:core:designsystem` -> `com.entsgra.reader.core.designsystem` */
internal val Project.derivedNamespace: String
	get() = "com.entsgra.reader" + path.replace(':', '.').replace('-', '_')

internal fun Project.configureKotlin() {
	tasks.withType<KotlinCompile>().configureEach {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}
}
