plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services") version "4.5.0" apply false
}

// The Firebase Android config is supplied per deployment and is not committed.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.project_final"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.project_final"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Địa chỉ backend. 10.0.2.2 là máy tính chủ khi chạy máy ảo Android.
        // Đổi bằng: gradle.properties -> riceguard.apiBaseUrl=http://192.168.1.10:8080
        val apiBase = (project.findProperty("riceguard.apiBaseUrl") as String?) ?: "http://10.0.2.2:8080"
        // Web client ID (loại "Web application") của project Firebase, dùng cho đăng nhập Google.
        // Lấy ở Firebase Console > Authentication > Sign-in method > Google > Web SDK configuration.
        // Đặt trong gradle.properties: riceguard.googleWebClientId=1234-abc.apps.googleusercontent.com
        val googleClientId = (project.findProperty("riceguard.googleWebClientId") as String?) ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "API_BASE_URL", "\"${apiBase.trimEnd('/')}/api/v1/\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.swiperefreshlayout)
    implementation(libs.exifinterface)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.glide)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
