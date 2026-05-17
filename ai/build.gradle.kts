import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.itlab.ai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            assets.srcDirs("src/main/assets", "$buildDir/generated/yolo26/assets")
        }
    }
    tasks.whenTaskAdded {
        if (name.contains("merge") && name.contains("NativeLibs")) {
            doLast {
                println("=== Merged dirs for $name:")
                fileTree("$buildDir/intermediates")
                    .filter { it.name.endsWith(".so") }
                    .forEach { println(it.parentFile.absolutePath) }
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val prepareYolo26Model =
    tasks.register<Exec>("prepareYolo26Model") {
        val outputAssetsDir = layout.buildDirectory.dir("generated/yolo26/assets")
        val workDir = layout.buildDirectory.dir("yolo26")
        val script = layout.projectDirectory.file("scripts/prepare_yolo26_model.py")
        val python =
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                providers.environmentVariable("PYTHON").orElse("python")
            } else {
                providers.environmentVariable("PYTHON").orElse("python3")
            }

        inputs.file(script)
        outputs.dir(outputAssetsDir)

        commandLine(
            python.get(),
            script.asFile.absolutePath,
            "--output-assets-dir",
            outputAssetsDir.get().asFile.absolutePath,
            "--work-dir",
            workDir.get().asFile.absolutePath,
        )
    }

tasks
    .matching {
        it.name == "preBuild" || (it.name.startsWith("merge") && it.name.endsWith("Assets"))
    }.configureEach {
        dependsOn(prepareYolo26Model)
    }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":domain"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.koin.android)
}

tasks.whenTaskAdded {
    if (name.contains("mergeDebugNativeLibs") || name.contains("mergeReleaseNativeLibs")) {
        doLast {
            copy {
                from("$rootDir/ai/src/main/jniLibs/arm64-v8a/openvino-2026.2.0")
                into("$buildDir/intermediates/merged_native_libs/debug/out/lib/arm64-v8a/openvino-2026.2.0")
            }
        }
    }
}
