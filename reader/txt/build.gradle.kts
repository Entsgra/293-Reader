plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
}

dependencies {
	api(project(":reader:api"))
	implementation(libs.kotlinx.coroutines.android)

	testImplementation(libs.kotlinx.coroutines.test)
}
