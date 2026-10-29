plugins {
	alias(libs.plugins.reader.android.feature)
}

dependencies {
	implementation(project(":core:data"))
	implementation(libs.androidx.paging.compose)
}
