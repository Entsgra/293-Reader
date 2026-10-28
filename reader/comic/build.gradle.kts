plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
}

dependencies {
	api(project(":reader:api"))
	implementation(project(":reader:archive"))
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.androidx.core.ktx)

	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.truth)
	androidTestImplementation(libs.kotlinx.coroutines.test)
	androidTestImplementation(libs.commons.compress)
}
