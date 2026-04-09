pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Resolve io.github.ivamsi artifacts from ~/.m2 first when present (demo mavenLocal testing).
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "EasyAndroidPermissions"
include(":easyandroidpermissions-core")
project(":easyandroidpermissions-core").projectDir = file("easyandroidpermissions")
include(":easyandroidpermissions-compose")
include(":EasyAndroidPermissionsDemo")