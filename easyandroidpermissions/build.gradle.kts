import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
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
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("io.github.ivamsi", "easyandroidpermissions-core", "2.1.0")

    pom {
        name.set("EasyAndroidPermissions Core")
        description.set("Coroutine-friendly runtime permission manager for Activities and Fragments")
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
        sourcesJar = SourcesJar.Sources(),
        // Dokka/Javadoc from AGP is skipped; stub javadoc is attached below — see publish-javadoc/README.txt.
        javadocJar = JavadocJar.None(),
    ))
}

val stubJavadocJar = tasks.register<Jar>("stubJavadocJar") {
    archiveClassifier.set("javadoc")
    from(layout.projectDirectory.dir("publish-javadoc"))
}

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications.withType<MavenPublication>().configureEach {
            artifact(stubJavadocJar)
        }
    }
}
