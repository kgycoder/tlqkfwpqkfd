plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.spellfix.korean"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spellfix.korean"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 별도의 키스토어/시크릿 없이 바로 설치할 수 있도록 debug 키로 서명합니다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// 외부 라이브러리(androidx 등)를 쓰지 않으므로 dependencies 블록이 비어 있습니다.
