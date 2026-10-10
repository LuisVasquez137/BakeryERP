import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Carga credenciales de firma desde keystore.properties (fuera de git).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    js {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    port = 8080
                }
            }
        }
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        // nonWasmMain: Firebase deps para Android, iOS y JS (excluye wasmJs)
        val nonWasmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.firebase.auth)
                implementation(libs.firebase.firestore)
                implementation(libs.firebase.storage)
                // Facturación electrónica: la app llama Cloud Functions (felGuardarPerfil…)
                // porque las credenciales de Digifact nunca pasan por Firestore del cliente.
                implementation(libs.firebase.functions)
            }
        }
        // nativeMobileMain: actual de initializePlatform para Android e iOS (NO JS)
        // JS necesita su propio actual porque inicializa Firebase con config web
        val nativeMobileMain by creating {
            dependsOn(nonWasmMain)
            dependencies {
                implementation(libs.koin.core)
            }
        }
        androidMain.get().dependsOn(nativeMobileMain)
        getByName("iosArm64Main").dependsOn(nativeMobileMain)
        getByName("iosSimulatorArm64Main").dependsOn(nativeMobileMain)
        jsMain.get().dependsOn(nonWasmMain)

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.coil.compose)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            implementation("androidx.biometric:biometric:1.1.0")
            implementation("androidx.core:core-splashscreen:1.0.1")
            // Push. El SDK de GitLive no cubre Messaging, así que en Android se usa
            // el SDK nativo directamente detrás de la interfaz PushTokenProvider.
            implementation("com.google.firebase:firebase-messaging:24.1.0")
            // Escáner de códigos de barras de Play Services. Se prefiere al de ML Kit con
            // CameraX porque NO requiere declarar ni pedir el permiso de cámara —el escaneo
            // corre en la UI de Google Play Services, no en la nuestra— y el módulo se
            // descarga bajo demanda en vez de sumar ~3 MB al APK. Mismo criterio que la
            // foto de factura, que usa ACTION_IMAGE_CAPTURE en vez de abrir la cámara.
            implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
            // Selfie del kiosko de asistencia. Aquí sí hace falta cámara propia (CameraX):
            // con la app de cámara del sistema se le puede tomar foto a otra foto, y la
            // detección de cara y parpadeo tiene que correr sobre la vista previa. Detector de
            // ML Kit "unbundled": el modelo lo baja Play Services, no engorda el APK.
            implementation("androidx.camera:camera-core:1.4.0")
            implementation("androidx.camera:camera-camera2:1.4.0")
            implementation("androidx.camera:camera-lifecycle:1.4.0")
            implementation("androidx.camera:camera-view:1.4.0")
            // ProcessCameraProvider.getInstance devuelve un ListenableFuture de Guava. Guava
            // ya viaja en el APK (lo traen Firebase/Play Services) pero no en el classpath de
            // compilación; se declara la MISMA versión para no sumar peso.
            implementation("com.google.guava:guava:32.1.3-android")
            implementation("com.google.android.gms:play-services-mlkit-face-detection:17.1.0")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // QR de verificación de la SAT en el ticket de hoja/PDF. La térmica dibuja el suyo con
            // ESC/POS; esto solo calcula la matriz (QRCodeProcessor), sin el render de cada plataforma.
            implementation(libs.qrcode.kotlin)
            implementation(libs.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.bakery.erp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.bakery.erp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 21
        versionName = "21"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

