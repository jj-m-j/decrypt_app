plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.decrypt.app"

    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.decrypt.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // 用 debug 签名，产出可直接安装的 release APK
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
}
