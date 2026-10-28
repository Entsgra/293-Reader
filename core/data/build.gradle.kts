plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
}

dependencies {
	api(project(":core:model"))
	api(project(":core:database"))
	implementation(project(":core:security"))
	implementation(libs.androidx.datastore.preferences)
	implementation(libs.kotlinx.coroutines.android)
}
