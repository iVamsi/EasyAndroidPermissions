import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.vanniktech.maven.publish") version "0.34.0"
}

android {
    namespace = "com.vamsi.easyandroidpermissions"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core Compose dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Essential AndroidX dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Optional dependencies
    compileOnly(libs.androidx.ui.tooling.preview)
    compileOnly(libs.androidx.material3)
    
    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    
    coordinates("io.github.ivamsi", "easyandroidpermissions", "1.0.0")

    pom {
        name.set("EasyAndroidPermissions")
        description.set("A lightweight Android library for easy handling of runtime permissions with Kotlin coroutines and Jetpack Compose")
        url.set("https://github.com/ivamsi/easyandroidpermissions")
        inceptionYear.set("2025")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("ivamsi")
                name.set("Vamsi Vaddavalli")
                url.set("https://github.com/ivamsi")
            }
        }

        scm {
            url.set("https://github.com/ivamsi/easyandroidpermissions")
            connection.set("scm:git:git://github.com/ivamsi/easyandroidpermissions.git")
            developerConnection.set("scm:git:ssh://git@github.com/ivamsi/easyandroidpermissions.git")
        }
    }

    configure(AndroidSingleVariantLibrary(
        variant = "release",
        sourcesJar = true,
        publishJavadocJar = true
    ))
}