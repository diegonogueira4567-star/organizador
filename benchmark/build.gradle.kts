plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

// Medições com Macrobenchmark contra a variante "benchmark" do app (R8 ligado, como no release).
android {
    namespace = "com.organizador.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // O emulador não é um aparelho de referência: os números são válidos para comparação, não absolutos.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}
