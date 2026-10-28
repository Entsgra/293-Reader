plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
}

dependencies {
	api(project(":reader:api"))
	implementation(project(":reader:archive"))
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.androidx.core.ktx)

	// Android's own XML pull parser, for JVM tests where android.util.Xml is a stub.
	testImplementation(libs.kxml2)

	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.truth)
	androidTestImplementation(libs.kotlinx.coroutines.test)
}
