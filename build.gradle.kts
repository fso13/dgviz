plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.dgviz"
version = "0.3.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot 4.1 / Spring Framework 7 / Spring Security 7
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    // YAML config (Jackson 3 dataformat)
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")

    // CLI
    implementation("info.picocli:picocli:4.7.7")
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    // CLI visualize — Jetty 12 / Servlet 6.1 (aligned with Boot 4)
    implementation("org.eclipse.jetty:jetty-server:12.1.12")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.1.12")

    // Maven model parsing (latest stable 3.9.x; 4.x still RC)
    implementation("org.apache.maven:maven-model:3.9.16")
    implementation("org.apache.maven:maven-model-builder:3.9.16")
    implementation("org.codehaus.plexus:plexus-xml:4.2.0")

    // Report exports
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    implementation("com.github.librepdf:openpdf:2.0.3")

    // Persistent HTTP sessions (survive restarts)
    implementation("org.springframework.session:spring-session-jdbc")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.cucumber:cucumber-java:8.0.3")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:8.0.3")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("dgviz.jar")
}

springBoot {
    mainClass.set("io.github.dgviz.DgvizBootApplication")
}
