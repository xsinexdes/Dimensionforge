plugins {
    java
}

group = "me.dimensionforge"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://libraries.minecraft.net/")
}

dependencies {
    // API de Paper para Minecraft 1.21.1
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // BetterModel (soft-depend). 2.2.0 es la última línea que soporta 1.21.1
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:2.2.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("project" to mapOf("version" to project.version.toString()))
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
}

tasks.jar {
    archiveFileName.set("DimensionForge-${project.version}.jar")
}
