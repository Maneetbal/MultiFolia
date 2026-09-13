version = "${properties["masterVersion"]}"

plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.4.0"
}

dependencies {
    implementation(project(":multipaper-mastermessagingprotocol"))
    implementation("org.yaml:snakeyaml:2.6")
    implementation("se.llbit:jo-nbt:1.3.0")
    implementation("io.netty:netty-all:4.2.7.Final")
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4")
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "puregero.multipaper.server.MultiPaperServer",
            "Minecraft-Version" to "${properties["mcVersion"]}",
            "Master-Version" to "${properties["masterVersion"]}"
        )
    }
}

tasks.shadowJar {
    relocate("io.netty", "puregero.multipaper.master.libs.netty")
    relocate("se.llbit.nbt", "puregero.multipaper.master.libs.nbt")
    relocate("org.yaml.snakeyaml", "puregero.multipaper.master.libs.snakeyaml")
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifact(tasks.jar)
    }
}
