pluginManagement {
	includeBuild("build-logic")
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "293-Reader"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:security")
include(":core:database")
include(":core:data")
include(":reader:api")
include(":reader:archive")
include(":reader:pdf")
include(":reader:comic")
include(":feature:library")
include(":feature:reader")
include(":feature:physical")
include(":feature:log")
include(":feature:settings")
