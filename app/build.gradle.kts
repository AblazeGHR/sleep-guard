plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.areyouasleep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.areyouasleep"
        // Android 8.0 是这条技术路线的天然下限：
        // AudioFocusRequest、NotificationChannel、VibrationEffect 都是 API 26 才有，
        // 再往下降到 23 就得为这三样各写一套兼容分支，收益（Android 6/7 存量）不值这个成本。
        minSdk = 26
        // 目标设备是鸿蒙 4.3 的 Android 兼容层（API 31），targetSdk 对齐可省掉
        // 高版本行为变更（edge-to-edge、FGS 类型强制、通知权限）带来的一堆分支。
        targetSdk = 31
        versionCode = 2
        versionName = "0.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // 自用侧载，不上 Google Play。targetSdk 刻意锁 31 对齐鸿蒙 4.x 的 Android 兼容层，
        // 同时免掉 Android 13+ 的 POST_NOTIFICATIONS 运行时申请。
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
