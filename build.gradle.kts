import com.google.protobuf.gradle.id
import net.researchgate.release.ReleaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "2.0.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion

    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.openapi.generator") version "7.6.0"

    id("com.google.protobuf") version "0.9.4"

    id("net.researchgate.release") version "3.0.2"
    id("com.palantir.git-version") version "3.1.0"

    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "org.devshred"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

val protoBufVersion = "4.27.1"
val jacksonVersion = "2.15.4"
val xmlunitVersion = "2.10.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.tika:tika-core:2.9.2")

    implementation("jakarta.validation:jakarta.validation-api")

    implementation("io.jenetics:jpx:3.1.0")
    implementation("com.garmin:fit:21.141.0")
    implementation("mil.nga:sf:2.2.2")
    implementation("mil.nga.sf:sf-geojson:3.3.3")

    implementation("com.google.protobuf:protobuf-kotlin:$protoBufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protoBufVersion")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("org.apache.commons:commons-lang3:3.15.0")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.apache.commons:commons-math3:3.6.1")
    testImplementation("org.xmlunit:xmlunit-core:$xmlunitVersion")
    testImplementation("org.xmlunit:xmlunit-matchers:$xmlunitVersion")
    testImplementation("org.xmlunit:xmlunit-assertj:$xmlunitVersion")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.4.2")
}

val generatedOpenApiSourcesDir = "${layout.buildDirectory.get()}/generated-openapi"

openApiGenerate {
    generatorName.set("kotlin-spring")

    inputSpec.set("src/main/spec/api-spec.yaml")
    outputDir.set(generatedOpenApiSourcesDir)

    configFile.set("src/main/spec/api-config.json")

    // MultipartFile definition due to bug in OAG
    // https://github.com/OpenAPITools/openapi-generator/issues/8333
    typeMappings.set(
        mapOf(
            "file+multipart" to "MultipartFile",
        ),
    )

    importMappings.set(
        mapOf(
            "MultipartFile" to "org.springframework.web.multipart.MultipartFile",
        ),
    )
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
    filter {
        exclude { entry -> entry.file.toString().contains("generated") }
    }
}

tasks.named("runKtlintCheckOverMainSourceSet").configure { dependsOn("openApiGenerate") }
