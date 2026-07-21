plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.binaryCompatibilityValidator)
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.kover)
    alias(libs.plugins.sigstore)
}

group = "de.micschro"
version = "0.3.0"

kotlin {
    explicitApi()
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll("-jvm-default=enable", "-Xjsr305=strict")
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
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.jqwik)
}

// Functional (TestKit) suite: real multi-module builds against the published plugin id.
// Custom suites are NOT auto-wired into `check`, so that is added below.
testing {
    suites {
        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junitBom.get())
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
            description = "Shards a multi-module build's test tasks across parallel CI nodes via Greedy-LPT. Requires Gradle 8.11+ and Java 17+."
            tags = listOf("ci", "testing", "sharding", "parallel", "build")
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

kover {
    reports {
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("detekt"))
    dependsOn(tasks.named("ktlintCheck"))
}
