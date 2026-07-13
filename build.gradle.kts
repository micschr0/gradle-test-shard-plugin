plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "2.4.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "de.micschro"
version = "0.1.0"

kotlin {
    explicitApi()
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xjsr305=strict")
    }
}

// java-gradle-plugin puts gradleApi() on the api configuration → leaks downstream.
// For redistributed plugins remove it and pin against the MINIMUM supported Gradle API.
configurations.named("api") {
    dependencies.removeIf { it is FileCollectionDependency }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    compileOnly("dev.gradleplugins:gradle-api:8.11")
    // Only for ProjectBuilder tests; does not leak downstream (test configurations are not published).
    testImplementation(gradleApi())
    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Functional (TestKit) suite: real multi-module builds against the published plugin id.
// java-gradle-plugin applies jvm-test-suite, auto-creating the functionalTest source set
// and Test task. Custom suites are NOT auto-wired into `check`, so that is added below.
testing {
    suites {
        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter("6.1.1")
            dependencies {
                implementation(gradleTestKit())
            }
        }
    }
}

gradlePlugin {
    website = "https://github.com/micschr0/gradle-test-shard-plugin"
    vcsUrl = "https://github.com/micschr0/gradle-test-shard-plugin"
    testSourceSets(sourceSets.getByName("functionalTest"))
    plugins {
        create("shardwise") {
            id = "de.micschro.shardwise"
            implementationClass = "de.micschro.shardwise.ShardwisePlugin"
            displayName = "Shardwise"
            description = "Shards a multi-module build's test tasks across parallel CI nodes via Greedy-LPT."
            tags = listOf("ci", "testing", "sharding", "parallel", "gitlab")
        }
    }
}

// Custom JVM test suites are not auto-wired into `check` — wire functionalTest explicitly.
tasks.named("check") { dependsOn(tasks.named("functionalTest")) }

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}

detekt {
    buildUponDefaultConfig = true
    config.from(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
}

tasks.named("check") { dependsOn(tasks.named("detekt")) }
