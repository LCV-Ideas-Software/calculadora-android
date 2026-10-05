// Teste do aplicativo minificado pelo R8, como caixa-preta (CALANDR-26). O
// módulo instala o build `minificado` do `:app` (o release, com a chave de
// depuração) e o percorre de fora do processo dele com o UI Automator, que é o
// caminho oficial para testar o build otimizado: os testes do `:app` rodam no
// debug, com dublês, e não exercitam o que o R8 pode quebrar. É o padrão dos
// módulos de teste do release na amostra oficial de Macrobenchmark:
// `targetProjectPath` e `self-instrumenting`.
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "dev.lcv.calculadora.teste.release"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 36
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // O par do `minificado` do `:app`; um módulo de teste só tem `debug` se não declarar outro.
        create("minificado") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    // O teste roda no próprio processo, e não no do aplicativo: o minificado fica intocado.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            localDevices {
                // O mesmo aparelho do `:app`, para a CI reaproveitar a imagem já baixada.
                create("pixel2api36") {
                    device = "Pixel 2"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

// Só a variante que testa o minificado: a `debug` testaria o debug do `:app`, que os testes dele já cobrem.
androidComponents {
    beforeVariants(selector().all()) { variante ->
        variante.enable = variante.buildType == "minificado"
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
