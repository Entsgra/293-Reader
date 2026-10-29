import java.util.Properties

plugins {
	alias(libs.plugins.reader.android.application)
	alias(libs.plugins.reader.android.compose)
	alias(libs.plugins.reader.hilt)
	alias(libs.plugins.kotlin.serialization)
}

// Release signing reads keystore.properties (gitignored). See keystore.properties.example.
val keystoreProperties = rootProject.file("keystore.properties")
	.takeIf { it.exists() }
	?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
	namespace = "com.entsgra.reader"

	defaultConfig {
		applicationId = "com.entsgra.reader"
		versionCode = 1
		versionName = "0.1.0"
	}

	signingConfigs {
		if (keystoreProperties != null) {
			create("release") {
				storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
				storePassword = keystoreProperties.getProperty("storePassword")
				keyAlias = keystoreProperties.getProperty("keyAlias")
				keyPassword = keystoreProperties.getProperty("keyPassword")
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			signingConfig = signingConfigs.findByName("release")
		}
	}

	packaging {
		resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
	}

	dependenciesInfo {
		// No dependency metadata blob in the APK; this is not a Play build.
		includeInApk = false
		includeInBundle = false
	}
}

dependencies {
	implementation(project(":core:designsystem"))
	implementation(project(":core:data"))
	implementation(project(":feature:library"))
	implementation(project(":feature:reader"))
	// Format readers register themselves into the BookFormatRegistry through Hilt.
	implementation(project(":reader:pdf"))
	implementation(project(":reader:comic"))
	implementation(project(":reader:epub"))
	implementation(project(":reader:txt"))
	implementation(project(":feature:physical"))
	implementation(project(":feature:log"))
	implementation(project(":feature:settings"))
	implementation(project(":feature:lock"))

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.process)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.kotlinx.serialization.core)
	implementation(libs.kotlinx.coroutines.android)
}
