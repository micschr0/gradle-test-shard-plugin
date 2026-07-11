pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

rootProject.name = "shardwise-e2e-consumer"

include("mod-a", "mod-b", "mod-c", "mod-d")
