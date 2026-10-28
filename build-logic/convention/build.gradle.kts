plugins {
	`kotlin-dsl`
}

group = "com.entsgra.reader.buildlogic"

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
	compilerOptions {
		jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
	}
}

dependencies {
	compileOnly(libs.android.gradlePlugin)
	compileOnly(libs.kotlin.gradlePlugin)
	compileOnly(libs.compose.gradlePlugin)
	compileOnly(libs.ksp.gradlePlugin)
	compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
	plugins {
		register("androidApplication") {
			id = "reader.android.application"
			implementationClass = "AndroidApplicationConventionPlugin"
		}
		register("androidLibrary") {
			id = "reader.android.library"
			implementationClass = "AndroidLibraryConventionPlugin"
		}
		register("androidCompose") {
			id = "reader.android.compose"
			implementationClass = "AndroidComposeConventionPlugin"
		}
		register("androidFeature") {
			id = "reader.android.feature"
			implementationClass = "AndroidFeatureConventionPlugin"
		}
		register("hilt") {
			id = "reader.hilt"
			implementationClass = "HiltConventionPlugin"
		}
		register("room") {
			id = "reader.room"
			implementationClass = "RoomConventionPlugin"
		}
		register("jvmLibrary") {
			id = "reader.jvm.library"
			implementationClass = "JvmLibraryConventionPlugin"
		}
	}
}
