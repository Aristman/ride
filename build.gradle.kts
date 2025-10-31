import java.io.File

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
    kotlin("plugin.serialization") version "2.1.0"
    // id("com.github.python-gradle-python") version "4.0.0" // Временно отключен - плагин не найден
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
}

// Конфигурация Python для MCP сервера
// python {
//     pip("fastapi>=0.104.0")
//     pip("uvicorn[standard]>=0.24.0")
//     pip("pydantic>=2.4.0")
//     pip("aiofiles>=23.2.0")
//     pip("python-multipart>=0.0.6")
//     pip("watchdog>=3.0.0")
//     pip("toml>=0.10.2")
//     pip("click>=8.1.0")
//     pip("httpx>=0.25.0")
// }

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }  // Открытая совместимость с будущими версиями
        }

        changeNotes = """
            Initial version
            - Поддержка Android Studio 2024.2+
            - Встроенный MCP сервер для файловой системы
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    // TODO: Исправить задачи MCP сервера после обновления Gradle
    // Задача для сборки MCP сервера
    /*
    val buildMcpServer by registering {
        group = "build"
        description = "Собрать MCP сервер файловой системы"
        doLast {
            val mcpServerDir = File(projectDir, "mcp-servers/filesystem-server")
            if (!mcpServerDir.exists()) {
                throw GradleException("MCP сервер не найден в ${mcpServerDir.absolutePath}")
            }

            // Устанавливаем зависимости и собираем пакет
            exec {
                workingDir = mcpServerDir
                commandLine = listOf("pip", "install", "-e", ".")
                standardOutput = System.out
                errorOutput = System.err
            }

            println("✅ MCP сервер файловой системы собран")
        }
    }

    // Задача для тестирования MCP сервера
    val testMcpServer by registering {
        group = "verification"
        description = "Протестировать MCP сервер"
        dependsOn(buildMcpServer)
        doLast {
            val mcpServerDir = File(projectDir, "mcp-servers/filesystem-server")

            exec {
                workingDir = mcpServerDir
                commandLine = listOf("python", "-m", "filesystem_server.main", "validate")
                standardOutput = System.out
                errorOutput = System.err
            }

            println("✅ MCP сервер прошел валидацию")
        }
    }

    // Добавляем зависимость к buildPlugin
    tasks.named<org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask>("buildPlugin") {
        dependsOn(buildMcpServer)
    }

    // Добавляем зависимость к test
    tasks.named<Test>("test") {
        dependsOn(testMcpServer)
    }
    */

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
            val configDir = File(layout.buildDirectory.get().asFile, "idea-sandbox/config").apply { mkdirs() }
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