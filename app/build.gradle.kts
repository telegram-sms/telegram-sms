// Helper extension function for executing commands
fun String.execute(): String {
    val parts = this.split("\\s".toRegex())
    val process = ProcessBuilder(*parts.toTypedArray()).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output
}

// For getting git information
fun getGitBranch(): String {
    // Prioritize getting the branch name from the GitLab CI environment variable.
    val ciBranch = System.getenv("CI_COMMIT_REF_NAME")
    if (ciBranch != null && ciBranch.isNotEmpty()) {
        println("Found CI branch: $ciBranch")
        return ciBranch
    }

    // If not in a CI environment (e.g., locally), fall back to using the git command.
    return try {
        val localBranch = "git rev-parse --abbrev-ref HEAD".execute().trim()
        println("Found local branch: $localBranch")
        localBranch
    } catch (e: Exception) {
        e.printStackTrace()
        println("Could not determine branch, falling back to 'unknown-branch'")
        "unknown-branch"
    }
}

// Get the branch name before configuring Android, so we can use it directly later.
val currentBranch = getGitBranch()

plugins {
    id("com.android.application")
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qwe7002.telegram_sms"
        minSdk = 23
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "Debug"
    }
    
/*    androidResources {
        generateLocaleConfig = true
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW", "zh-rHK", "yue-rCN", "yue-rHK", "ja-rJP", "es-rES", "ru", "vi"))
    }*/
    
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        register("release") {
            val keystoreFile = file("keys.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = if (project.hasProperty("KEYSTORE_PASS")) project.property("KEYSTORE_PASS") as String else System.getenv("KEYSTORE_PASS")
                keyAlias = if (project.hasProperty("ALIAS_NAME")) project.property("ALIAS_NAME") as String else System.getenv("ALIAS_NAME")
                keyPassword = if (project.hasProperty("ALIAS_PASS")) project.property("ALIAS_PASS") as String else System.getenv("ALIAS_PASS")
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            if (currentBranch == "nightly") {
                applicationIdSuffix = ".nightly"
            }

            // Only apply signing config if keystore exists
            if (file("keys.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                //noinspection ChromeOsAbiSupport
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }

        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }
    namespace = "com.qwe7002.telegram_sms"
    buildToolsVersion = "36.1.0"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.browser:browser:1.9.0")
    implementation("com.squareup.okio:okio:3.16.4")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.3.2")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.tencent:mmkv:2.3.0")
    implementation("com.github.yuriy-budiyev:code-scanner:2.1.0")
    implementation("androidx.core:core-ktx:1.17.0")
    //noinspection GradleDependency
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    implementation("androidx.activity:activity-ktx:1.12.3")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.goterl:lazysodium-android:5.2.0@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    testImplementation("junit:junit:4.13.2")
}

tasks.register<Copy>("copy_language_pack") {
    description = "Copy the language pack to the source directory, replace the placeholder file"
    from("language_pack/")
    into("src/main/res/")
    exclude("**/README.md")
    include("**/*")
}

tasks.register<Delete>("clean_language_pack") {
    description = "Clean up copied language pack directories from the source directory"

    // Delete entire language pack directories
    delete(file("src/main/res/values-zh-rCN"))
    delete(file("src/main/res/values-zh-rTW"))
    delete(file("src/main/res/values-zh-rHK"))
    delete(file("src/main/res/values-yue-rCN"))
    delete(file("src/main/res/values-yue-rHK"))
    delete(file("src/main/res/values-ja-rJP"))
    delete(file("src/main/res/values-es-rES"))
    delete(file("src/main/res/values-ru"))
    delete(file("src/main/res/values-vi"))

    doLast {
        println("Language pack directories cleaned from src/main/res/")
    }
}
