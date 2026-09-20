// Interface do aplicativo: Compose, ViewModels e a aplicação Hilt. Toda a regra
// de negócio vive no `:core:calc` e todo o acesso a dados no `:core:data`; este
// módulo só apresenta e coleta.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.lcv.calculadora"
    // Android 17 (API 37): o OkHttp 5.5 exige compilar contra a API 37 ou
    // superior. `targetSdk` acompanha, para que o aplicativo se declare feito
    // para a versão contra a qual compila (lint OldTargetApi).
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "dev.lcv.calculadora"
        // Android 14 (decisão do operador, 19/09/2026): o `java.time` do motor é
        // nativo, sem core library desugaring.
        minSdk = 34
        targetSdk = 37
        // O `versionCode` 1 foi consumido pela publicação na trilha `internal`
        // de 17/09/2026 (run 35272361221); o Google Play recusa um código já
        // usado, então a primeira versão pública sobe como 2.
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        // `BuildConfig.VERSION_NAME` é a fonte da versão no `User-Agent`: o
        // `:core:data` exige essa identidade e não conhece o manifesto.
        buildConfig = true
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

    // Release signing is injected by the publishing workflow through the
    // android.injected.signing.* properties, so no key material and no
    // password is ever written into this repository.
}

/**
 * Os três textos que a tela de licenças mostra são os próprios arquivos da raiz
 * do repositório. Copiá-los para dentro de `app/src/main/assets` criaria uma
 * segunda versão, que divergiria da primeira correção; esta tarefa os leva aos
 * assets no build, pela API oficial de fontes geradas do AGP.
 */
abstract class ReunirLicencas : DefaultTask() {
    @get:InputFiles
    abstract val arquivos: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destino: DirectoryProperty

    @TaskAction
    fun executar() {
        val pasta = destino.get().asFile
        pasta.mkdirs()
        arquivos.forEach { origem ->
            origem.copyTo(destino.file(origem.name).get().asFile, overwrite = true)
        }
    }
}

androidComponents {
    onVariants { variante ->
        val tarefa = tasks.register<ReunirLicencas>(
            "reunirLicencas" + variante.name.replaceFirstChar { it.uppercase() },
        ) {
            arquivos.from(
                rootProject.file("LICENSE"),
                rootProject.file("NOTICE"),
                rootProject.file("THIRDPARTY.md"),
            )
        }
        variante.sources.assets?.addGeneratedSourceDirectory(tarefa, ReunirLicencas::destino)
    }
}

dependencies {
    // O `:core:data` reexporta o `:core:calc` (`api`), então o motor chega junto.
    implementation(project(":core:data"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // A variante JUnit 5 do kotlin-test tem de ser explícita num módulo
    // Android: só o plugin kotlin.jvm a escolhe sozinho pela plataforma.
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)

    // Os testes de Compose usam JUnit 4, que é o que o executor instrumentado
    // do AndroidX entende; não conflita com o JUnit 5 dos testes de JVM.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
