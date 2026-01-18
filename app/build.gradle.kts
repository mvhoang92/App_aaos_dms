plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.dms"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.dms"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // THÊM DÒNG NÀY: Để chạy được Instrumented Tests
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnit4Runner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Cho phép sử dụng thư viện Car của hệ thống khi chạy trên xe
    useLibrary("android.car")
}

dependencies {
    // Standard dependencies (Khuyên dùng libs.xxx từ catalog)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Car App Library (Dùng cho UI và Templates)
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-automotive:1.4.0")

    // Services & Media
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.media:media:1.7.0")

    // LƯU Ý: Xóa dòng androidx.car:car:1.0.0-alpha1 nếu bị xung đột với android.car hệ thống
    // implementation("androidx.car:car:1.0.0-alpha1")

    // --- FIX LỖI CarPropertyEvent BÁO ĐỎ ---
    val sdkDir = project.android.sdkDirectory.path
    compileOnly(files("$sdkDir/platforms/android-34/optional/android.car.jar"))

    // --- FIX LỖI Unresolved reference: test (Junit & AndroidTest) ---
    testImplementation(libs.junit) // Cho src/test
    androidTestImplementation(libs.androidx.junit) // Cho src/androidTest (Sửa lỗi "test" đỏ)
    androidTestImplementation(libs.androidx.espresso.core) // Hỗ trợ chạy test trên emulator
}
