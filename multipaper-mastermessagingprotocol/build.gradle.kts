plugins {
    java
    `maven-publish`
}

version = "${properties["masterVersion"]}-${properties["mcVersion"]}"

dependencies {
    compileOnly("io.netty:netty-all:4.2.7.Final")
}

publishing {
    publications.create<MavenPublication>("maven") {
        artifact(tasks.jar)
    }
}
