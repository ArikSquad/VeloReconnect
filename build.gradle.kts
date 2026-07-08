plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "dev.ari"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    implementation("de.exlll:configlib-yaml:4.8.1")
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.0.0")
    compileOnly("net.elytrium.limboapi:api:1.1.27")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("velocity-plugin.json") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
