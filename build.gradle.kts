plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7" apply false
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.m13"
version = "1.8"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.nexomc.com/releases/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.nexomc:nexo:1.0.0")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks {
    jar {
        archiveBaseName.set("EggHunt")
    }
    shadowJar {
        archiveBaseName.set("EggHunt")
        archiveClassifier.set("")
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
