plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
}

dependencies {
	api(libs.tink.android)
	implementation(libs.androidx.core.ktx)
}
