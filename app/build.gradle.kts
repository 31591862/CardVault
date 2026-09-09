import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 签名信息从 local.properties 读取（该文件不入版本库，避免密码泄露到 GitHub）。
// 换机器 / 其他协作者克隆后，在本地 local.properties 里补这四行即可签名。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.cardvault"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cardvault"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 正式签名：与 QuickPay 共用同一张证书（路径和密码在 local.properties）
        // 证书 + 密码丢了 = 永远无法更新已安装的 App，务必备份
        create("releaseKey") {
            val storePath = localProps.getProperty("RELEASE_STORE_FILE")
            val storePass = localProps.getProperty("RELEASE_STORE_PASSWORD")
            check(storePath != null && storePass != null) {
                "local.properties 缺少 RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD，release 构建无法签名"
            }
            storeFile = file(storePath)
            storePassword = storePass
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("releaseKey")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 指纹 / 人脸解锁
    implementation(libs.androidx.biometric)
    // BiometricPrompt 要求宿主是 FragmentActivity
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
