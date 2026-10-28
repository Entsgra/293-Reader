plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.android.compose)
}

dependencies {
	api(project(":core:model"))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.core.ktx)
}
