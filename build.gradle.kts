plugins {
    java
    id("org.springframework.boot") version "3.2.5"
}

group = "com.krypton"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
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
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.5"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    
    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.3.0")
    
    // Apache PDFBox for PDF extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.2")
    
    // Google Gen AI / Gemini SDK (or client library)
    implementation("com.google.cloud:google-cloud-vertexai:1.5.0")
    
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
