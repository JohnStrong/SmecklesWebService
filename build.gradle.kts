plugins {
    id("java")
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "shoppinglistserviceshoppinglistservice"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}


dependencies {
    // JUnit
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Spring Boot Web (includes Spring MVC)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring Data JPA for database operations
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // PostgreSQL Driver
    implementation("org.postgresql:postgresql:42.7.2")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Boot Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // H2 in-memory database for testing
    runtimeOnly("com.h2database:h2")
}

tasks.test {
    useJUnitPlatform()
}