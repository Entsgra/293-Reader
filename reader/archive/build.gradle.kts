plugins {
	alias(libs.plugins.reader.android.library)
}

android {
	defaultConfig {
		consumerProguardFiles("consumer-rules.pro")
	}
}

dependencies {
	api(project(":reader:api"))
	implementation(libs.commons.compress)
	// LZMA and LZMA2, the usual 7z codecs. Optional in commons-compress, so declared here.
	implementation(libs.xz)

	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.truth)
}
