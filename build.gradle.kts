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
version = "0.4.1" // x-release-please-version

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

// The e2e consumer hardcodes the plugin version in its `plugins {}` block, which
// Gradle evaluates before the script and so cannot read a shared value. This task
// keeps the copy honest: it fails `check` locally and in CI the moment the consumer
// drifts from the root version — long before the slow e2e pipeline would catch it.
// CC-safe: the version and file text are captured as plain values at configuration
// time, so the task action closes over no project references.
val rootVersion = version.toString()
val consumerBuildText = providers.fileContents(
    layout.projectDirectory.file("e2e/consumer/build.gradle.kts"),
).asText
tasks.register("verifyConsumerVersion") {
    val expected = rootVersion
    val text = consumerBuildText.get()
    doLast {
        val consumerVersion = Regex("""id\("de\.micschro\.shardwise"\) version "([^"]+)"""")
            .find(text)?.groupValues?.get(1)
            ?: error("could not find the shardwise plugin version in e2e/consumer/build.gradle.kts")
        require(consumerVersion == expected) {
            "version drift: root is $expected but e2e/consumer/build.gradle.kts pins $consumerVersion — update the consumer"
        }
    }
}
tasks.named("check") { dependsOn("verifyConsumerVersion") }

tasks.withType<Test>().configureEach { useJUnitPlatform() }

// Reproducible jars: strip timestamps and fix entry order so an independent
// rebuild byte-matches the signed/published artifact.
tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

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

// The plugin defaults to `build/sigstore/<taskName>/`. The release workflow
// collects jars and bundles from `build/libs`, so redirect the jar signatures
// there. Only the pluginMaven task signs the jars; the marker publication signs
// only a `pom-default.xml`, whose bundle name would collide with pluginMaven's
// pom bundle in a shared directory — leave that one in its own default dir.
tasks.withType<dev.sigstore.sign.tasks.SigstoreSignFilesTask>().configureEach {
    enabled = sigstoreSigningEnabled
    if (name == "sigstoreSignPluginMavenPublication") {
        signatureDirectory = layout.buildDirectory.dir("libs")
    }
}

// `publishPlugins` uploads to the Portal without pulling in the publication's
// signing tasks, so a release would ship unsigned bundles. Only wire them when
// signing is on — local and e2e publishes have no OIDC provider.
if (sigstoreSigningEnabled) {
    tasks.named("publishPlugins") {
        dependsOn(tasks.withType<dev.sigstore.sign.tasks.SigstoreSignFilesTask>())
    }
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
