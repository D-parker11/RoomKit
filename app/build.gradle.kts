
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.roomkit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.roomkit"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets.getByName("test").java.srcDir("src/testShared/java")
    sourceSets.getByName("androidTest").java.srcDir("src/testShared/java")
    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:core-ktx:1.5.0")

    // WorkManager (Proper Kotlin DSL syntax)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.zxing:core:3.5.3")
}
val testJavaToolchains =
    extensions.getByType<
            org.gradle.jvm.toolchain.JavaToolchainService
            >()

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    javaLauncher.set(
        testJavaToolchains.launcherFor {
            languageVersion.set(
                org.gradle.jvm.toolchain.JavaLanguageVersion.of(17)
            )
        }
    )
}