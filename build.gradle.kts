import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.9.22"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion

    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"

    id("kotlinx-serialization") version kotlinVersion

    id("net.researchgate.release") version "3.0.2"
    id("com.palantir.git-version") version "3.0.0"

    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "org.devshred"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.tika:tika-core:2.9.1")

    implementation("io.jenetics:jpx:3.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
    implementation("io.jenetics:jpx:3.1.0")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.apache.commons:commons-lang3:3.14.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks {
    dependencyUpdates {
        resolutionStrategy {
            componentSelection {
                all {
                    val rejected =
                        listOf(
                            "alpha",
                            "beta",
                            "rc",
                            "cr",
                            "m",
                            "preview",
                            "b",
                            "ea",
                        )
                            .map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-+]*") }
                            .any { it.matches(candidate.version) }
                    if (rejected) {
                        reject("Release candidate")
                    }
                }
            }
        }
    }
}

tasks.processResources {
    filesMatching("git.properties") { expand(project.properties) }
}

val gitVersion: groovy.lang.Closure<String> by extra
