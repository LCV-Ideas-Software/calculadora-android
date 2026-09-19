plugins {
    alias(libs.plugins.android.application)
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
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing is injected by the publishing workflow through the
    // android.injected.signing.* properties, so no key material and no
    // password is ever written into this repository.
}
