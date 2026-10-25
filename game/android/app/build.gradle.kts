plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.sesgaro.control_angulos"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID
        // (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.sesgaro.gato"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter.
        // (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P
        // force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // phone: standard app. quest: Meta Quest 3 2D panel. Horizon OS is Android, so code is
    // shared; src/quest/AndroidManifest.xml supplies the different manifest. Separate IDs
    // allow both apps to coexist.
    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            manifestPlaceholders["appLabel"] = "Tic-Cat-Toe"
        }
        create("quest") {
            dimension = "device"
            applicationIdSuffix = ".quest"
            versionNameSuffix = "-quest"
            manifestPlaceholders["appLabel"] = "Tic-Cat-Toe (Quest)"
            // These rules prevent R8 from breaking Spatial SDK release builds; see the referenced file.
            proguardFiles("proguard-quest.pro")
            // Quest 3 runs Android 12L+ and needs Android 12 BLUETOOTH_SCAN/CONNECT permissions for BLE.
            minSdk = 32
        }
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

// Meta Spatial SDK is Quest-only. GameActivity3D is an immersive activity alongside the 2D
// panel. Scenes use procedural meshes, so the Spatial Editor Gradle plugin is unnecessary.
val metaSpatialSdkVersion = "0.14.0"
dependencies {
    "questImplementation"("com.meta.spatial:meta-spatial-sdk:$metaSpatialSdkVersion")
    "questImplementation"("com.meta.spatial:meta-spatial-sdk-toolkit:$metaSpatialSdkVersion")
    "questImplementation"("com.meta.spatial:meta-spatial-sdk-vr:$metaSpatialSdkVersion")
    // Interaction SDK: virtual hands and palm grabbing of the head.
    "questImplementation"("com.meta.spatial:meta-spatial-sdk-isdk:$metaSpatialSdkVersion")
}
