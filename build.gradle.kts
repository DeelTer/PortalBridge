plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.4.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "ru.deelter.portalbridge"
version = "1.0.0"
description = "PortalBridge - cross-server portals with trust verification"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.3-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    compileOnly("org.slf4j:slf4j-simple:2.0.16")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("net.kyori:adventure-platform-bukkit:4.3.4")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    jar { enabled = false }
    runServer {
        minecraftVersion("1.21.3")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }
    shadowJar {
        archiveFileName.set("PortalBridge-${project.version}.jar")
        relocate("org.bstats", "${project.group}.shaded.bstats")
        relocate("com.github.benmanes.caffeine", "${project.group}.shaded.caffeine")
    }
    assemble { dependsOn(shadowJar) }
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val props = mapOf("version" to version, "description" to description)
        filesMatching("plugin.yml") { expand(props) }
    }
}