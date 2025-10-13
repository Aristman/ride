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
        create("IC", "2024.3")
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

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }  // Открытая совместимость с будущими версиями
        }

        changeNotes = """
            Initial version
            - Поддержка IntelliJ IDEA 2024.3+
            - Поддержка Android Studio 2024.3+
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
                "-Dgradle-jvm-compatibility.disabled=true",
                "-Dcom.intellij.gradle.jvm.support.skip=true",
                "-Dfile.encoding=UTF-8",
                "-Dconsole.encoding=UTF-8")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}