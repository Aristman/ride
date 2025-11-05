package ru.marslab.ide.ride.testing

/**
 * JVM-детектор тестового фреймворка. В реальной реализации использует сведения
 * из ProjectStructureProvider (build.gradle / pom.xml зависимости), полученные через A2A.
 */
class JvmTestFrameworkDetector(
    private val structureProvider: ProjectStructureProvider
) : TestFrameworkDetector {

    override suspend fun detect(): TestFramework {
        // TODO: извлечь зависимости из ProjectStructure (Gradle/Maven)
        // Heuristic placeholder: вернуть JUNIT5 по умолчанию, если build-system Gradle/Maven; иначе NONE
        val structure = structureProvider.getProjectStructure()
        val build = structure.buildSystem
        return when (build) {
            BuildSystem.GRADLE, BuildSystem.MAVEN -> TestFramework.JUNIT5
            else -> TestFramework.NONE
        }
    }

    override suspend fun suggestAddInstructions(framework: TestFramework): String {
        val structure = structureProvider.getProjectStructure()
        val bs = structure.buildSystem
        return when (framework) {
            TestFramework.JUNIT5 -> when (bs) {
                BuildSystem.GRADLE -> """
                    // build.gradle.kts
                    dependencies {
                        testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
                    }
                    tasks.test { useJUnitPlatform() }
                """.trimIndent()
                BuildSystem.MAVEN -> """
                    <!-- pom.xml -->
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.10.2</version>
                      <scope>test</scope>
                    </dependency>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-surefire-plugin</artifactId>
                          <version>3.2.5</version>
                          <configuration>
                            <useModulePath>false</useModulePath>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                """.trimIndent()
                else -> defaultNote()
            }
            TestFramework.KOTEST -> when (bs) {
                BuildSystem.GRADLE -> """
                    // build.gradle.kts
                    dependencies {
                        testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
                        testImplementation("io.kotest:kotest-assertions-core:5.9.1")
                    }
                    tasks.test { useJUnitPlatform() }
                """.trimIndent()
                BuildSystem.MAVEN -> """
                    <!-- pom.xml -->
                    <dependency>
                      <groupId>io.kotest</groupId>
                      <artifactId>kotest-runner-junit5</artifactId>
                      <version>5.9.1</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>io.kotest</groupId>
                      <artifactId>kotest-assertions-core</artifactId>
                      <version>5.9.1</version>
                      <scope>test</scope>
                    </dependency>
                """.trimIndent()
                else -> defaultNote()
            }
            TestFramework.TESTNG -> when (bs) {
                BuildSystem.GRADLE -> """
                    // build.gradle.kts
                    dependencies {
                        testImplementation("org.testng:testng:7.10.2")
                    }
                    tasks.test { useTestNG() }
                """.trimIndent()
                BuildSystem.MAVEN -> """
                    <!-- pom.xml -->
                    <dependency>
                      <groupId>org.testng</groupId>
                      <artifactId>testng</artifactId>
                      <version>7.10.2</version>
                      <scope>test</scope>
                    </dependency>
                """.trimIndent()
                else -> defaultNote()
            }
            TestFramework.NONE -> defaultNote()
        }
    }

    private fun defaultNote(): String = "Фреймворк будет добавлен после определения сборочной системы проекта."
}
