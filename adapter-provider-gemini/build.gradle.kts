plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-domain"))
    implementation("org.springframework:spring-webflux:6.1.14")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
