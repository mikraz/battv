plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma: con le variabili BATTV_KEYSTORE e BATTV_KEYSTORE_PASSWORD l'APK è firmato sempre con la stessa chiave,
// così si aggiorna sopra la versione installata senza perdere accesso e preferiti.
val keystorePath: String? = System.getenv("BATTV_KEYSTORE")

android {
    namespace = "io.github.mikraz.battv"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.mikraz.battv"
        minSdk = 21
        targetSdk = 35
        versionCode = (System.getenv("BATTV_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("BATTV_VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("battv") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BATTV_KEYSTORE_PASSWORD")
                keyAlias = "battv"
                keyPassword = System.getenv("BATTV_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("battv") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Il player web è lo stesso index.html della Tesla, copiato dentro l'app a ogni build
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("web").get().asFile)
}

val copyWeb by tasks.registering(Copy::class) {
    from(rootProject.file("../index.html"))
    into(layout.buildDirectory.dir("web"))
}
tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
    val media3 = "1.5.1"
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
