plugins {
	alias(libs.plugins.reader.android.feature)
	alias(libs.plugins.reader.android.screenshots)
}

dependencies {
	implementation(project(":core:data"))
}
