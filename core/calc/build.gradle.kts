// Motor de cálculo: Kotlin puro, sem dependência de Android. Toda a aritmética
// financeira roda na JVM, o que permite testá-la sem emulador a cada mudança.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    // Mesmo JDK usado pela CI e pela publicação.
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    // O Gradle não adiciona o launcher da JUnit Platform sozinho; sem ele os
    // testes não são descobertos.
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
