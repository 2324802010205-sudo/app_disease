plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.riceguard"
version = "1.0.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("com.google.firebase:firebase-admin:9.11.0")
    implementation("com.cloudinary:cloudinary-http5:2.5.0")
    implementation("com.anthropic:anthropic-java:2.34.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }
tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }

// Chạy cục bộ không cần khóa Firebase/Cloudinary thật: dùng Firebase Emulator (auth + firestore) và lưu ảnh vào thư mục uploads/.
//   1) firebase emulators:start --only auth,firestore --project demo-riceguard
//   2) ./gradlew bootRunLocal
tasks.register<org.springframework.boot.gradle.tasks.run.BootRun>("bootRunLocal") {
    group = "application"
    description = "Chạy backend với Firebase Emulator và lưu ảnh cục bộ (không cần khóa thật)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.riceguard.api.RiceGuardApplication")
    environment(
        mapOf(
            "FIREBASE_EMULATOR" to "true",
            "FIREBASE_PROJECT_ID" to "demo-riceguard",
            "FIRESTORE_EMULATOR_HOST" to "127.0.0.1:8085",
            "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:9099",
            "FIREBASE_WEB_API_KEY" to "fake",
            "FIREBASE_IDENTITY_TOOLKIT_URL" to "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1",
            "FIREBASE_SECURE_TOKEN_URL" to "http://127.0.0.1:9099/securetoken.googleapis.com/v1",
            "MEDIA_PROVIDER" to "local",
            // Máy ảo Android truy cập máy tính chủ qua 10.0.2.2
            "PUBLIC_BASE_URL" to (System.getenv("PUBLIC_BASE_URL") ?: "http://10.0.2.2:8080")
        )
    )
}
