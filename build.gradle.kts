plugins {
	kotlin("jvm") version "1.9.22"
	kotlin("plugin.spring") version "1.9.22"
	id("org.springframework.boot") version "3.3.2"
	id("io.spring.dependency-management") version "1.1.4"
	kotlin("plugin.jpa") version "1.9.22"
	kotlin("plugin.serialization") version "1.9.22"
}

group = "com.vangelnum"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-rest")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
	implementation("org.springframework.boot:spring-boot-starter-websocket")

	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// PostgreSQL
	runtimeOnly("org.postgresql:postgresql")
	// ChatGPT
	implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0-M6"))
	implementation("org.springframework.ai:spring-ai-spring-boot-autoconfigure")
	implementation("org.springframework.ai:spring-ai-openai")

	// Mapstruct
	implementation("org.mapstruct:mapstruct:1.5.5.Final")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")

	// Jackson
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// Tests
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")

	val testContainers = "1.19.8"
	testImplementation("org.testcontainers:testcontainers:$testContainers")
	testImplementation(platform("org.testcontainers:testcontainers-bom:$testContainers"))
	testImplementation("org.testcontainers:postgresql:$testContainers")
	testImplementation("org.testcontainers:junit-jupiter:$testContainers")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	implementation("org.jsoup:jsoup:1.15.4")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

configure<SourceSetContainer> {
	named("main") {
		java.srcDir("src/main/kotlin")
	}
}

