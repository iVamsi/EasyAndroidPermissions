import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.vamsi.easyandroidpermissions.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    api(project(":easyandroidpermissions-core"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// CI publishes to mavenLocal without a GPG key. On by default so a release
// is never published unsigned by accident.
val signingEnabled = providers.gradleProperty("enableSigning").getOrElse("true").toBoolean()

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    if (signingEnabled) {
        signAllPublications()
    }

    coordinates("io.github.ivamsi", "easyandroidpermissions-compose", libs.versions.easyandroidpermissions.get())

    pom {
        name.set("EasyAndroidPermissions Compose")
        description.set("Jetpack Compose helpers for EasyAndroidPermissions")
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

