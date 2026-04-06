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
    testImplementation(libs.dotenv.java)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("jna.library.path", layout.projectDirectory.dir("native/lib").asFile.absolutePath)
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description =
        "Compare JNA (AgcArchive) vs agc CLI getctg. Requires agc on PATH and native/lib — use: pixi run benchmark"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.btmonier.agckt.benchmark.AgcBenchmarkKt")
    workingDir = layout.projectDirectory.asFile
    jvmArgs("-Djna.library.path=${layout.projectDirectory.dir("native/lib").asFile.absolutePath}")
}

tasks.register<JavaExec>("demo") {
    group = "verification"
    description =
        "Run Demo.kt (set AGC_ARCHIVE_PATH in .env or env; requires native/lib for JNA)"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.btmonier.agckt.benchmark.DemoKt")
    workingDir = layout.projectDirectory.asFile
    jvmArgs("-Djna.library.path=${layout.projectDirectory.dir("native/lib").asFile.absolutePath}")
}

tasks.register<JavaExec>("benchmarkReal") {
    group = "verification"
    description =
        "JNA vs agc CLI on AGC_ARCHIVE_PATH (.env), chr1–chr10; needs agc on PATH and native/lib"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.btmonier.agckt.benchmark.RealWorldAgcBenchmarkKt")
    workingDir = layout.projectDirectory.asFile
    jvmArgs(
        "-Djna.library.path=${layout.projectDirectory.dir("native/lib").asFile.absolutePath}",
        "-Xmx512m",
    )
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt-config.yml"))
}

ktlint {
    version.set("1.5.0")
}
