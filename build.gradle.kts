// Gradle build script for ComposerAI API
// Configures Spring Boot 3.4.2, Java 25, Spotless, and SpotBugs
plugins {
    java
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.spotbugs") version "6.0.18"
}

group = "com.composerai"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
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
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Jackson
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

    // OpenAI
    implementation("com.openai:openai-java:4.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Qdrant
    implementation("io.qdrant:client:1.7.0")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.guava:guava:32.1.3-jre")

    // Parsing / Conversion
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.sun.mail:jakarta.mail:2.0.1")

    // Flexmark (Markdown)
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-html2md-converter:0.64.8")

    // ImageIO
    implementation("com.twelvemonkeys.imageio:imageio-core:3.12.0")
    implementation("com.twelvemonkeys.imageio:imageio-bmp:3.12.0")

    // Dotenv
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Spotbugs
    spotbugs("com.github.spotbugs:spotbugs:4.8.6")
    spotbugs("com.h3xstream.findsecbugs:findsecbugs-plugin:1.13.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.classformat.ignore", "true")
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "-Djdk.attach.allowAttachSelf=true",
        "-Dmockito.mock-maker=subclass",
        "--add-opens",
        "java.base/java.lang=ALL-UNNAMED",
    )
}

spotless {
    java {
        target("src/**/*.java")
        // Use Palantir Java Format (safer for newer JDKs)
        palantirJavaFormat("2.83.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// Spotbugs configuration
spotbugs {
    ignoreFailures.set(true)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.HIGH)
    excludeFilter.set(file("spotbugs-exclude.xml"))
}

// Java 25 preview support if needed, though most things should just work with 25
// JVM arguments required for Lombok compatibility with JDK 25+ (access to internal compiler APIs)
val lombokJvmArgs =
    listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
    )

tasks.withType<JavaCompile> {
    options.isFork = true
    options.compilerArgs.add("-parameters")
    options.forkOptions.jvmArgs = lombokJvmArgs
}

springBoot {
    mainClass.set("com.composerai.api.ComposerAiApiApplication")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    systemProperty("spring.classformat.ignore", "true")
}
