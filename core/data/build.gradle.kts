// Camada de dados: fontes de cotação, cache local e persistência do backtest.
// Android library, porque Room e Hilt dependem da plataforma; a regra de
// negócio continua toda no `:core:calc`, que este módulo apenas alimenta.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.lcv.calculadora.data"
    // Android 17 (API 37): o OkHttp 5.5 exige compilar contra a API 37 ou
    // superior.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        // Mesmo mínimo do `:app` (Android 14, decisão do operador de 19/09/2026).
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        // Mesmo JDK usado pela CI e pela publicação; com o Kotlin embutido do
        // AGP, o alvo JVM do Kotlin segue este valor.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

room {
    // Esquema versionado: é o que permite escrever migrações quando a
    // estrutura mudar, em vez de descartar os dados do aparelho.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:calc"))

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // A variante JUnit 5 do kotlin-test tem de ser explícita num módulo
    // Android: só o plugin kotlin.jvm a escolhe sozinho pela plataforma.
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
}
