import java.io.File

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "ru.marslab.ide"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2024.2.5")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Добавляем JetBrains Runtime с JCEF поддержкой
        jetbrainsRuntime()

        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")

        // Отключаем проблемные плагины для избежания конфликта версий Java
        // excludePlugin не поддерживается в этой версии, используем другой подход
    }
    // Временно исключаем Gradle plugin для обхода ошибки
    // implementation("org.jetbrains.plugins.gradle:org.jetbrains.plugins.gradle.gradle-java-extensions.gradle") {
    //     exclude(group = "org.jetbrains.plugins.gradle", module = "gradle-jvm-compatibility")
    // }

    // Kotlinx Serialization (для JSON, БЕЗ Ktor)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // XML serialization via xmlutil (kotlinx-serialization-xml)
    implementation("io.github.pdvrieze.xmlutil:core:0.86.3")
    implementation("io.github.pdvrieze.xmlutil:serialization:0.86.3")

    // Tiktoken для подсчёта токенов
    implementation("com.knuddels:jtokkit:1.0.0")

    // SQLite для хранения эмбеддингов
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    // JUnit 5 (Jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    // JUnit 4 API still used by part of the test suite
    testImplementation("junit:junit:4.13.2")
    // Enable running JUnit 3/4 tests on JUnit Platform
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    // Mockito for existing tests using mockito, any(), verify(), etc.
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    // Inline mock maker for final classes (often needed in Kotlin code)
    testImplementation("org.mockito:mockito-inline:5.2.0")

    // A2A smoke tests (isolated) use the same testImplementation classpath
}

// Isolated source set for headless A2A smoke tests
sourceSets {
    val a2aTest by creating {
        java.srcDir("src/a2aTest/kotlin")
        resources.srcDir("src/a2aTest/resources")
        compileClasspath += sourceSets.main.get().output + configurations.testCompileClasspath.get()
        runtimeClasspath += output + configurations.testRuntimeClasspath.get() + compileClasspath
    }
}

configurations {
    named("a2aTestImplementation") {
        extendsFrom(testImplementation.get())
    }
    named("a2aTestRuntimeOnly") {
        extendsFrom(testRuntimeOnly.get())
    }
}

tasks {
    // Isolated test task for A2A smoke tests
    register<Test>("a2aTest") {
        description = "Runs A2A headless smoke tests"
        group = "verification"
        testClassesDirs = sourceSets["a2aTest"].output.classesDirs
        classpath = sourceSets["a2aTest"].runtimeClasspath
        useJUnit()
        // Run only our smoke test by default
        include("**/A2AAgentsSmokeTest.class")
    }

    // Use JUnit Platform for default tests (Jupiter)
    withType<Test> {
        useJUnitPlatform()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }  // Открытая совместимость с будущими версиями
        }

        changeNotes = """
            Initial version
            - Поддержка Android Studio 2024.2+
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    // Add system properties to workaround Gradle JVM compatibility issue
    runIde {
        jvmArgs("-Didea.ignore.disabled.plugins=true",
                "-Didea.plugins.disabled.plugins=com.intellij.gradle,org.jetbrains.plugins.gradle",
                "-Dgradle-jvm-compatibility.disabled=true",
                "-Dcom.intellij.gradle.jvm.support.skip=true",
                // Включаем JCEF без sandbox (Linux): устраняет Embedded Browser is suspended
                "-Dide.browser.jcef.sandbox.enable=false",
                // Необязательный флаг: отключить GPU для стабильности (иногда помогает на Linux)
                "-Dide.browser.jcef.gpu.disable=true",
                "-Dfile.encoding=UTF-8",
                "-Dconsole.encoding=UTF-8")

        // Гарантированно отключаем bundled Gradle plugin через sandbox-конфиг
        doFirst {
            val configDir = File(buildDir, "idea-sandbox/config").apply { mkdirs() }
            val optionsDir = File(configDir, "options").apply { mkdirs() }
            // Перечень плагинов для отключения, по одному в строке (возможные ID)
            val entries = listOf(
                "com.intellij.gradle",
                "org.jetbrains.plugins.gradle"
            )
            File(configDir, "disabled_plugins.txt").writeText(entries.joinToString("\n", postfix = "\n"))
            File(optionsDir, "disabled_plugins.txt").writeText(entries.joinToString("\n", postfix = "\n"))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}