plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
	alias(libs.plugins.reader.room)
}

android {
	defaultConfig {
		consumerProguardFiles("consumer-rules.pro")
	}
}

dependencies {
	api(project(":core:model"))
	implementation(project(":core:security"))
	implementation(libs.sqlcipher.android)
	implementation(libs.androidx.sqlite)
	api(libs.androidx.room.paging)
	implementation(libs.kotlinx.coroutines.android)

	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.truth)
	androidTestImplementation(libs.kotlinx.coroutines.test)
}
