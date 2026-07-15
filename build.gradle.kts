plugins {
    kotlin("jvm") version "2.4.0"
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    `maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("saibon") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    "implementation"("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    "implementation"("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
    "implementation"("net.fabricmc:fabric-language-kotlin:${project.property("fabric_language_kotlin_version")}")
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)

    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

kotlin {
    jvmToolchain(25)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE") {
        rename { "${it}_$projectName" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        // Configured later, when release publishing is set up (Stage 3).
    }
}
