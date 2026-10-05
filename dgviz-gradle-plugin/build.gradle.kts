plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.dgviz"
version = "0.3.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("dgvizScan") {
            id = "io.github.dgviz.scan"
            displayName = "DGViz dependency scan"
            description = "Sends Gradle dependency tree / SBOM to DGViz and writes an MR report"
            implementationClass = "io.github.dgviz.gradle.DgvizPlugin"
        }
    }
}
