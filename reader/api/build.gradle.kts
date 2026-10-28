plugins {
	alias(libs.plugins.reader.android.library)
}

dependencies {
	api(project(":core:model"))
	implementation(libs.kotlinx.coroutines.core)
}
