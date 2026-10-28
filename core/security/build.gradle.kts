plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.hilt)
}

dependencies {
	api(libs.tink.android)
	implementation(libs.bouncycastle.bcprov)
	implementation(libs.androidx.core.ktx)
	implementation(libs.kotlinx.coroutines.android)

	androidTestImplementation(libs.androidx.test.runner)
	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.truth)
}
