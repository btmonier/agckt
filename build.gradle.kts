plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    `java-library`
}

group = property("group") as String
version = property("version") as String

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

dependencies {
    api(libs.jna)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("jna.library.path", layout.projectDirectory.dir("native/lib").asFile.absolutePath)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt-config.yml"))
}

ktlint {
    version.set("1.5.0")
}
