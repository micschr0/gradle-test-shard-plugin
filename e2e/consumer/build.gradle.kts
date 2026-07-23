import de.micschro.shardwise.PlanDetail

plugins {
    id("de.micschro.shardwise") version "0.4.2" // x-release-please-version (kept in sync with root by release-please)
}

// Driven by the e2e pipeline (see e2e/scripts/run-node.sh) so one fixture serves
// every scenario. Absent properties leave the plugin's own defaults in place.
shardwise {
    (findProperty("shardwise.weights") as String?)?.let {
        weightsFile.set(layout.projectDirectory.file(it))
    }
    (findProperty("shardwise.planDetail") as String?)?.let {
        planDetail.set(PlanDetail.valueOf(it))
    }
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.14.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
