import org.jetbrains.kotlin.konan.properties.Properties
//import kotlin.random.Random
//import kotlin.random.nextInt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties()
if (rootProject.file("local.properties").canRead())
    localProperties.load(rootProject.file("local.properties").inputStream())
//val v = Random.nextInt(1..10000)
android {
    namespace = "com.wuyou.notification.lyric"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.wuyou.notification.lyric"
        minSdk = 26
        targetSdk = 35
//        versionCode = v
//        versionName = "$v.0"
        versionCode = 11
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val config = localProperties.getProperty("androidStoreFile")?.let {
        signingConfigs.create("config") {
            storeFile = file(it)
            storePassword = localProperties.getProperty("androidStorePassword")
            keyAlias = localProperties.getProperty("androidKeyAlias")
            keyPassword = localProperties.getProperty("androidKeyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        all {
            signingConfig = config ?: signingConfigs["debug"]
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.ezXHelper)
    implementation(libs.lyric.getter.api)
    compileOnly(libs.xposed.api)
    implementation(libs.androidx.core.ktx)
}