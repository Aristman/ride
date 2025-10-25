package ru.marslab.ide.ride.scanner

import ru.marslab.ide.ride.model.scanner.ProjectFilterConfig
import ru.marslab.ide.ride.model.scanner.ProjectType

/**
 * Провайдер конфигураций фильтрации для разных типов проектов
 */
object ProjectFilterConfigProvider {

    private val commonExcludePatterns = listOf(
        // VCS и метаданные
        "**/.git/**",
        "**/.svn/**",
        "**/.hg/**",
        "**/.bzr/**",
        "**/.cvs/**",
        "**/.idea/**",
        "**/.vscode/**",
        "**/.eclipse/**",
        "**/.netbeans/**",
        "**/.DS_Store",
        "**/Thumbs.db",
        "**/desktop.ini",

        // Временные файлы
        "**/*.tmp",
        "**/*.temp",
        "**/*.bak",
        "**/*.backup",
        "**/*.old",
        "**/*.orig",
        "**/*.swp",
        "**/*.swo",
        "**/*~",
        "**/*.log",
        "**/*.lock",
        "**/*.lck",

        // IDE и редакторы
        "**/.vs/**",
        "**/.vscode/**",
        "**/.fleet/**",
        "**/.codium/**",
        "**/*.sublime-*",

        // ОС файлы
        "**/.DS_Store/**",
        "**/._*",
        "**/.Spotlight-V100/**",
        "**/.Trashes/**",
        "**/ehthumbs.db",
        "**/Desktop.ini",
        "**/RECYCLER/**",

        // Бинарные и медиа файлы
        "**/*.jar",
        "**/*.war",
        "**/*.ear",
        "**/*.zip",
        "**/*.tar",
        "**/*.tar.gz",
        "**/*.tgz",
        "**/*.rar",
        "**/*.7z",
        "**/*.exe",
        "**/*.dll",
        "**/*.so",
        "**/*.dylib",
        "**/*.app",
        "**/*.deb",
        "**/*.rpm",
        "**/*.dmg",
        "**/*.pkg",
        "**/*.msi",
        "**/*.png",
        "**/*.jpg",
        "**/*.jpeg",
        "**/*.gif",
        "**/*.bmp",
        "**/*.tiff",
        "**/*.svg",
        "**/*.ico",
        "**/*.mp3",
        "**/*.mp4",
        "**/*.avi",
        "**/*.mov",
        "**/*.wav",
        "**/*.pdf",
        "**/*.doc",
        "**/*.docx",
        "**/*.xls",
        "**/*.xlsx",
        "**/*.ppt",
        "**/*.pptx"
    )

    private val javaExcludePatterns = listOf(
        // Build и дистрибутивы
        "**/target/**",
        "**/build/**",
        "**/out/**",
        "**/bin/**",
        "**/classes/**",
        "**/generated-sources/**",
        "**/generated-test-sources/**",
        "**/test-results/**",
        "**/reports/**",

        // Зависимости
        "**/.gradle/**",
        "**/gradle/wrapper/**",
        "**/.m2/**",
        "**/node_modules/**",

        // IDE
        "**/.idea/**",
        "**/.vscode/**",
        "**/.eclipse/**",
        "**/.metadata/**",
        "**/.recommenders/**",

        // Временные и кэш
        "**/.gradle/**",
        "**/build/tmp/**",
        "**/build/.gradle/**",
        "**/.apt_generated/**",
        "**/.classpath",
        "**/.project",
        "**/.settings/**",

        // Генерированные файлы
        "**/generated/**",
        "**/generated/**",
        "**/generated-sources/**",
        "**/auto-imports.d",
        "**/external_libs.txt",
        "**/*.generated.java",
        "**/*.generated.kt",

        // Логи и временные файлы
        "**/*.log",
        "**/*.log.*",
        "**/logs/**",
        "**/tmp/**",
        "**/temp/**",

        // Бинарные файлы
        "**/*.jar",
        "**/*.war",
        "**/*.ear",
        "**/*.aar",
        "**/*.class",
        "**/*.dex",

        // Документация и метаданные
        "**/javadoc/**",
        "**/docs/build/**",
        "**/README.md",
        "**/CHANGELOG.md",
        "**/LICENSE*",
        "**/NOTICE*",

        // CI/CD
        "**/.github/**",
        "**/.gitlab-ci.yml",
        "**/.travis.yml",
        "**/Jenkinsfile",
        "**/azure-pipelines.yml",

        // База данных
        "**/*.db",
        "**/*.sqlite",
        "**/*.h2.db",
        "**/database/**",

        // Конфигурация окружения
        "**/.env*",
        "**/application-*.yml",
        "**/application-*.yaml",
        "**/application-*.properties"
    )

    private val pythonExcludePatterns = listOf(
        // Python специфичные
        "**/__pycache__/**",
        "**/*.pyc",
        "**/*.pyo",
        "**/*.pyd",
        "**/.Python/**",
        "**/pip-log.txt",
        "**/pip-delete-this-directory.txt",
        "**/tox.ini",
        "**/.coverage",
        "**/.pytest_cache/**",
        "**/.mypy_cache/**",
        "**/.coverage.*",
        "**/htmlcov/**",
        "**/cover/**",
        "**/nosetests.xml",
        "**/coverage.xml",
        "**/*.cover",
        "**/.hypothesis/**",
        "**/build/**",
        "**/develop-eggs/**",
        "**/dist/**",
        "**/downloads/**",
        "**/eggs/**",
        "**/.eggs/**",
        "**/lib/**",
        "**/lib64/**",
        "**/parts/**",
        "**/sdist/**",
        "**/var/**",
        "**/wheels/**",
        "**/*.egg-info/**",
        "**/.installed.cfg",
        "**/*.egg",
        "**/MANIFEST",
        "**/site/**",

        // Virtual environments
        "**/.venv/**",
        "**/venv/**",
        "**/env/**",
        "**/ENV/**",
        "**/env.bak/**",
        "**/venv.bak/**",

        // IDE
        "**/.idea/**",
        "**/.vscode/**",
        "**/.spyderproject/**",
        "**/.rope_project/**",

        // Jupyter notebooks checkpoints
        "**/.ipynb_checkpoints/**",

        // Базы данных
        "**/*.db",
        "**/*.sqlite",
        "**/*.sqlite3"
    )

    private val nodeJsExcludePatterns = listOf(
        // Node.js специфичные
        "**/node_modules/**",
        "**/npm-debug.log*",
        "**/yarn-debug.log*",
        "**/yarn-error.log*",
        "**/lerna-debug.log*",
        "**/.pnpm-debug.log*",
        "**/.yarn/**",
        "**/.pnpm/**",
        "**/.npm/**",

        // Build дистрибутивы
        "**/dist/**",
        "**/build/**",
        "**/out/**",
        "**/.next/**",
        "**/.nuxt/**",
        "**/.cache/**",
        "**/.temp/**",
        "**/.tmp/**",

        // Coverage и тесты
        "**/coverage/**",
        "**/.nyc_output/**",
        "**/junit.xml",

        // IDE
        "**/.idea/**",
        "**/.vscode/**",

        // Environment
        "**/.env*",
        "**/.env.local",
        "**/.env.development.local",
        "**/.env.test.local",
        "**/.env.production.local",

        // Бинарные и ассеты
        "**/*.tgz",
        "**/*.tar.gz",
        "**/public/**",
        "**/assets/**",
        "**/static/**"
    )

    private val rustExcludePatterns = listOf(
        // Rust специфичные
        "**/target/**",
        "**/Cargo.lock",
        "**/debug/**",
        "**/release/**",
        "**/doc/**",
        "**/examples/target/**",

        // IDE
        "**/.idea/**",
        "**/.vscode/**",
        "**/rustfmt.toml",

        // Coverage
        "**/tarpaulin-report.html",
        "**/cobertura.xml",

        // Бинарные файлы
        "**/*.rlib"
    )

    /**
     * Получает конфигурацию фильтрации для указанного типа проекта
     */
    fun getFilterConfig(projectType: ProjectType): ProjectFilterConfig {
        val excludePatterns = when (projectType) {
            ProjectType.MAVEN, ProjectType.GRADLE, ProjectType.GRADLE_KOTLIN,
            ProjectType.SPRING_BOOT, ProjectType.ANDROID -> {
                commonExcludePatterns + javaExcludePatterns
            }
            ProjectType.PYTHON -> {
                commonExcludePatterns + pythonExcludePatterns
            }
            ProjectType.NODE_JS -> {
                commonExcludePatterns + nodeJsExcludePatterns
            }
            ProjectType.RUST -> {
                commonExcludePatterns + rustExcludePatterns
            }
            ProjectType.GENERIC -> {
                commonExcludePatterns
            }
            ProjectType.UNKNOWN -> {
                commonExcludePatterns
            }
        }.distinct()

        val binaryExtensions = when (projectType) {
            ProjectType.MAVEN, ProjectType.GRADLE, ProjectType.GRADLE_KOTLIN,
            ProjectType.SPRING_BOOT, ProjectType.ANDROID -> {
                setOf("jar", "war", "ear", "aar", "class", "dex")
            }
            ProjectType.PYTHON -> {
                setOf("pyc", "pyo", "pyd")
            }
            ProjectType.NODE_JS -> {
                setOf("tgz", "tar.gz")
            }
            ProjectType.RUST -> {
                setOf("rlib")
            }
            else -> emptySet()
        }

        return ProjectFilterConfig(
            projectType = projectType,
            excludePatterns = excludePatterns,
            maxFileSize = getMaxFileSize(projectType),
            binaryExtensions = binaryExtensions
        )
    }

    /**
     * Получает максимальный размер файла для типа проекта
     */
    private fun getMaxFileSize(projectType: ProjectType): Long {
        return when (projectType) {
            ProjectType.PYTHON, ProjectType.NODE_JS -> 5 * 1024 * 1024 // 5MB
            ProjectType.RUST -> 20 * 1024 * 1024 // 20MB (компилятор может генерировать большие файлы)
            else -> 10 * 1024 * 1024 // 10MB по умолчанию
        }
    }

    /**
     * Получает include паттерны для исходных файлов по типу проекта
     */
    fun getSourceFilePatterns(projectType: ProjectType): List<String> {
        return when (projectType) {
            ProjectType.MAVEN, ProjectType.GRADLE, ProjectType.GRADLE_KOTLIN,
            ProjectType.SPRING_BOOT -> {
                listOf(
                    "**/*.java",
                    "**/*.kt",
                    "**/*.scala",
                    "**/*.groovy",
                    "**/*.xml",
                    "**/*.properties",
                    "**/*.yml",
                    "**/*.yaml",
                    "**/*.json"
                )
            }
            ProjectType.ANDROID -> {
                listOf(
                    "**/*.java",
                    "**/*.kt",
                    "**/*.xml",
                    "**/*.gradle",
                    "**/*.gradle.kts",
                    "**/*.json",
                    "**/*.properties"
                )
            }
            ProjectType.PYTHON -> {
                listOf(
                    "**/*.py",
                    "**/*.pyx",
                    "**/*.pyi",
                    "**/*.cfg",
                    "**/*.ini",
                    "**/*.toml",
                    "**/*.yaml",
                    "**/*.yml",
                    "**/*.json",
                    "**/*.txt",
                    "**/*.md",
                    "**/*.rst"
                )
            }
            ProjectType.NODE_JS -> {
                listOf(
                    "**/*.js",
                    "**/*.ts",
                    "**/*.jsx",
                    "**/*.tsx",
                    "**/*.json",
                    "**/*.md",
                    "**/*.yml",
                    "**/*.yaml",
                    "**/*.toml",
                    "**/*.html",
                    "**/*.css",
                    "**/*.scss",
                    "**/*.less"
                )
            }
            ProjectType.RUST -> {
                listOf(
                    "**/*.rs",
                    "**/*.toml",
                    "**/*.yaml",
                    "**/*.yml",
                    "**/*.json",
                    "**/*.md"
                )
            }
            ProjectType.GENERIC -> {
                listOf(
                    "**/*.java",
                    "**/*.kt",
                    "**/*.py",
                    "**/*.js",
                    "**/*.ts",
                    "**/*.rs",
                    "**/*.go",
                    "**/*.cpp",
                    "**/*.c",
                    "**/*.h",
                    "**/*.hpp",
                    "**/*.php",
                    "**/*.rb",
                    "**/*.swift",
                    "**/*.dart",
                    "**/*.scala",
                    "**/*.groovy",
                    "**/*.xml",
                    "**/*.json",
                    "**/*.yaml",
                    "**/*.yml",
                    "**/*.properties",
                    "**/*.toml",
                    "**/*.txt",
                    "**/*.md"
                )
            }
            else -> emptyList()
        }
    }
}