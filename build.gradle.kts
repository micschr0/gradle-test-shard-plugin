import org.gradle.plugin.compatibility.compatibility

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
version = "0.4.0"

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
            description = "Gradle test sharding & splitting for CI: balances test tasks across parallel nodes by measured runtime"
            tags = listOf("ci", "testing", "sharding", "test-splitting", "parallel")
            compatibility {
                features {
                    configurationCache = true
                }
            }
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

// Sigstore signing needs an OIDC identity provider. That exists on the release
// workflow, not in local builds or the e2e sandbox, where `publishToMavenLocal`
// would otherwise fail on "Failed to obtain signing certificate".
// Release publishing sets SIGSTORE_SIGN=true.
// `enabled` is a plain Boolean resolved at configuration time. An `onlyIf` spec
// would capture a script reference, which the configuration cache cannot serialize.
val sigstoreSigningEnabled =
    providers.environmentVariable("SIGSTORE_SIGN").orNull == "true"

tasks.withType<dev.sigstore.sign.tasks.SigstoreSignFilesTask>().configureEach {
    enabled = sigstoreSigningEnabled
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
