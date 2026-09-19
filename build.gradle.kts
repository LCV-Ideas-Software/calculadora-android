plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // O Android Gradle Plugin 9 traz o Kotlin embutido com uma versão mínima do
    // Kotlin Gradle Plugin; declarar o KGP aqui fixa a versão do catálogo para
    // todos os módulos, inclusive o `:core:calc`, que é JVM puro.
    alias(libs.plugins.kotlin.jvm) apply false
    // Processadores de anotação do `:core:data` (Room e Hilt) via KSP, o
    // mecanismo suportado pelo Kotlin embutido do AGP 9 (o kapt não é).
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
