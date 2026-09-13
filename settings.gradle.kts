pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "multipaper"

include("multipaper-api")
include("multipaper-server")
include("multipaper-master")
include("multipaper-mastermessagingprotocol")
