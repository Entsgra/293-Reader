// Shared setup for the Roborazzi screenshot tests; only ever a test dependency.
plugins {
	alias(libs.plugins.reader.android.library)
	alias(libs.plugins.reader.android.compose)
}

dependencies {
	api(project(":core:designsystem"))
	api(platform(libs.androidx.compose.bom))
	api(libs.androidx.compose.ui.test.junit4)
	api(libs.robolectric)
	api(libs.roborazzi)
	api(libs.roborazzi.compose)
	api(libs.roborazzi.junit.rule)
	api(libs.junit)
	// Roborazzi brings Espresso 3.5, which cannot find SDK 37's InputManager.
	api(libs.androidx.test.espresso.core)
}
