plugins {
	alias(libs.plugins.reader.android.feature)
	alias(libs.plugins.reader.android.screenshots)
}

dependencies {
	implementation(project(":core:data"))
	implementation(project(":feature:lock"))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.paging.compose)
}
