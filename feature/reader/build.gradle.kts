plugins {
	alias(libs.plugins.reader.android.feature)
	alias(libs.plugins.reader.android.screenshots)
}

dependencies {
	implementation(project(":core:data"))
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
}
