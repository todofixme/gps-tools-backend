import com.google.protobuf.gradle.id
import net.researchgate.release.ReleaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "2.1.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion

    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.12.0"

    id("com.google.protobuf") version "0.9.5"

    id("net.researchgate.release") version "3.1.0"
    id("com.palantir.git-version") version "3.2.0"

    id("com.github.ben-manes.versions") version "0.52.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"

    id("com.intershop.gradle.jaxb") version "7.0.1"
}

group = "org.devshred"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

val protoBufVersion = "4.30.2"
val jacksonVersion = "2.18.3"
val xmlunitVersion = "2.10.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.tika:tika-core:3.1.0")

    implementation("io.jenetics:jpx:3.2.1")
    implementation("com.garmin:fit:21.158.0")
    implementation("mil.nga:sf:2.2.2")
    implementation("mil.nga.sf:sf-geojson:3.3.3")

    implementation("com.google.protobuf:protobuf-kotlin:$protoBufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protoBufVersion")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("org.apache.commons:commons-lang3:3.17.0")

    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.apache.commons:commons-math3:3.6.1")
    testImplementation("org.xmlunit:xmlunit-core:$xmlunitVersion")
    testImplementation("org.xmlunit:xmlunit-matchers:$xmlunitVersion")
    testImplementation("org.xmlunit:xmlunit-assertj:$xmlunitVersion")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.4.4")
}

val generatedOpenApiSourcesDir = "${layout.buildDirectory.get()}/generated-openapi"

openApiGenerate {
    generatorName.set("kotlin-spring")

    inputSpec.set("src/main/spec/api-spec.yaml")
    outputDir.set(generatedOpenApiSourcesDir)

    configFile.set("src/main/spec/api-config.json")
}

java.sourceSets["main"].java.srcDir(generatedOpenApiSourcesDir)

tasks.withType<KotlinCompile> {
    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            jvmTarget.set(JVM_17)
        }
    }
    dependsOn(tasks.openApiGenerate)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

configure<ReleaseExtension> {
    preTagCommitMessage = "chore(release): pre tag commit "
    tagCommitMessage = "chore(release): creating tag "
    newVersionCommitMessage = "chore(release): new version commit "
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
                        ).map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-+]*") }
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

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protoBufVersion"
    }
    generateProtoTasks {
        all().forEach {
            it.builtins {
                id("kotlin")
            }
        }
    }
}

ktlint {
    version.set("1.5.0")
    filter {
        exclude { entry -> entry.file.toString().contains("generated") }
    }
}

tasks.named("runKtlintCheckOverMainSourceSet").configure { dependsOn("openApiGenerate") }

jaxb {
    javaGen {
        register("tcx") {
            schema = file("src/main/schema/TrainingCenterDatabasev2.xsd")
        }
        register("tpx") {
            schema = file("src/main/schema/ActivityExtensionv2.xsd")
        }
    }
}
