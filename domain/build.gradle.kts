import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Módulo Kotlin puro: modelos, regras de negócio, motor de recorrência e contratos de repositório.
// Não depende de Android, o que permite testes unitários rápidos e impede acoplamento com a UI.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
