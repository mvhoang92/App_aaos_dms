// Block này quản lý kho lưu trữ cho các plugin Gradle
pluginManagement {
    repositories {
        google() // <-- Rất quan trọng
        mavenCentral()
        gradlePluginPortal()
    }
}

// Block này quản lý kho lưu trữ cho các thư viện (dependencies) của dự án
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // <-- QUAN TRỌNG NHẤT: Thư viện car-app nằm ở đây
        mavenCentral()
    }
}

rootProject.name = "Dms"
include(":app")

