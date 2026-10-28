plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
}

dependencies {
	api(project(":core:model"))
	api(project(":core:database"))
	api(project(":reader:api"))
	implementation(project(":core:security"))
	implementation(libs.androidx.datastore.preferences)
	implementation(libs.kotlinx.coroutines.android)

	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.truth)
	androidTestImplementation(libs.kotlinx.coroutines.test)
}
