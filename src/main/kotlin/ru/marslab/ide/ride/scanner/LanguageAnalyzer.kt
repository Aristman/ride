package ru.marslab.ide.ride.scanner

import java.nio.file.Path

/**
 * Анализатор языков программирования и подсчета строк кода
 */
object LanguageAnalyzer {

    /**
     * Информация о строках кода в файле
     */
    data class CodeMetrics(
        val totalLines: Int,
        val codeLines: Int,
        val commentLines: Int,
        val blankLines: Int,
        val language: String,
        val complexity: FileComplexity
    )

    /**
     * Уровень сложности файла
     */
    enum class FileComplexity {
        LOW,      // < 100 строк кода
        MEDIUM,   // 100-500 строк кода
        HIGH,     // 500-1000 строк кода
        VERY_HIGH // > 1000 строк кода
    }

    private fun startsWithBytes(arr: ByteArray, prefix: ByteArray): Boolean {
        if (arr.size < prefix.size) return false
        for (i in prefix.indices) {
            if (arr[i] != prefix[i]) return false
        }
        return true
    }

    /**
     * Определяет язык программирования.
     * Сначала по расширению/имени файла, затем (если не удалось) по содержимому.
     */
    fun detectLanguage(filePath: Path): String {
        val fileName = filePath.fileName.toString()
        val extension = fileName.substringAfterLast('.', "").lowercase()

        // 1) По расширению
        val byExtension = when (extension) {
            // JVM языки
            "java" -> "Java"
            "kt", "kts" -> "Kotlin"
            "scala", "sc" -> "Scala"
            "groovy", "gvy" -> "Groovy"
            "clj", "cljs", "cljc", "edn" -> "Clojure"

            // Python
            "py", "pyw", "pyc", "pyo", "pyd", "pyi", "pyx", "pxd" -> "Python"

            // JavaScript/TypeScript
            "js", "mjs", "cjs" -> "JavaScript"
            "ts", "tsx", "mts", "cts" -> "TypeScript"
            "jsx" -> "JSX"
            "vue" -> "Vue"

            // C/C++
            "c", "h" -> "C"
            "cpp", "cxx", "cc", "c++", "hpp", "hxx", "hh", "h++" -> "C++"

            // C#
            "cs" -> "C#"

            // Go
            "go" -> "Go"

            // Rust
            "rs" -> "Rust"

            // Swift
            "swift" -> "Swift"

            // Web технологии
            "html", "htm" -> "HTML"
            "css" -> "CSS"
            "scss", "sass" -> "SCSS"
            "less" -> "Less"
            "styl" -> "Stylus"

            // Базы данных
            "sql" -> "SQL"
            "json" -> "JSON"
            "xml" -> "XML"
            "yaml", "yml" -> "YAML"

            // Shell скрипты
            "sh", "bash", "zsh", "fish" -> "Shell"
            "bat", "cmd" -> "Batch"
            "ps1" -> "PowerShell"

            // Другие
            "rb", "rbw" -> "Ruby"
            "php", "php3", "php4", "php5", "phtml" -> "PHP"
            "dart" -> "Dart"
            "lua" -> "Lua"
            "r" -> "R"
            "m" -> "MATLAB/Octave"
            "pl", "pm", "t", "pod" -> "Perl"
            "ex", "exs" -> "Elixir"
            "erl", "hrl" -> "Erlang"
            "hs", "lhs" -> "Haskell"
            "ml", "mli" -> "OCaml"
            "fs", "fsi", "fsx" -> "F#"
            "ada", "adb", "ads" -> "Ada"
            "d" -> "D"
            "nim" -> "Nim"
            "zig" -> "Zig"
            "v" -> "V"
            "cr" -> "Crystal"

            // Конфигурационные файлы
            "toml" -> "TOML"
            "ini" -> "INI"
            "cfg", "conf" -> "Config"
            "properties" -> "Properties"
            "env" -> "Environment"

            // Документация
            "md", "markdown" -> "Markdown"
            "rst" -> "reStructuredText"
            "txt" -> "Text"

            else -> null
        }

        if (byExtension != null) return byExtension

        // 2) По имени файла без расширения
        val byFilename = when (fileName) {
            "Dockerfile", "docker-compose.yml", "docker-compose.yaml" -> "Docker"
            "Makefile", "makefile" -> "Makefile"
            "CMakeLists.txt" -> "CMake"
            "Rakefile", "Gemfile" -> "Ruby"
            "package.json", "yarn.lock", "package-lock.json" -> "Node.js"
            "pom.xml", "build.gradle", "build.gradle.kts" -> "Build"
            "requirements.txt", "setup.py", "pyproject.toml" -> "Python"
            "Cargo.toml", "Cargo.lock" -> "Rust"
            else -> null
        }
        if (byFilename != null) return byFilename

        // 3) По содержимому (shebang, сигнатуры, MIME)
        return detectByContent(filePath) ?: "Unknown"
    }

    /**
     * Анализирует файл и подсчитывает метрики кода
     */
    fun analyzeFile(filePath: Path): CodeMetrics? {
        return try {
            val content = filePath.toFile().readText()
            val language = detectLanguage(filePath)
            analyzeCodeContent(content, language)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Контентное определение языка/типа файла.
     * Возвращает null, если однозначно определить не удалось.
     */
    private fun detectByContent(filePath: Path): String? {
        // 3.1) Shebang
        try {
            readFirstLine(filePath)?.let { firstLine ->
                if (firstLine.startsWith("#!")) {
                    val sb = firstLine.lowercase()
                    return when {
                        sb.contains("python") -> "Python"
                        sb.contains("node") -> "JavaScript"
                        sb.contains("bash") || sb.contains("sh") || sb.contains("zsh") -> "Shell"
                        sb.contains("ruby") -> "Ruby"
                        sb.contains("perl") -> "Perl"
                        else -> null
                    }
                }
            }
        } catch (_: Exception) {}
        // 3.2) Magic bytes (быстрые сигнатуры)
        try {
            val header = readFirstBytes(filePath, 8)
            if (header != null) {
                // PDF
                if (startsWithBytes(header, "%PDF-".toByteArray())) return "PDF"
                // ZIP family (docx/xlsx/jar/apk/aar)
                if (startsWithBytes(header, byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))) return "ZIP"
                // ELF
                if (header.size >= 4 && startsWithBytes(header, byteArrayOf(0x7F.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) return "ELF"
                // PNG
                val pngSig = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
                if (header.size >= 8 && startsWithBytes(header, pngSig)) return "PNG"
                // GIF
                if (header.size >= 3 && startsWithBytes(header, byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte()))) return "GIF"
                // JPEG
                if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) return "JPEG"
            }
        } catch (_: Exception) {}
        // 3.3) Текстовые эвристики
        try {
            readFirstNonEmptyTrimmed(filePath)?.let { line ->
                if (line.startsWith("<?xml")) return "XML"
                if (line.startsWith("<!DOCTYPE html", true) || line.startsWith("<html", true)) return "HTML"
                if (line.startsWith("{")) return "JSON"
                if (line.startsWith("[")) return "JSON" // частый случай JSON массивов
                if (line.startsWith("---") || line.contains(":") && !line.contains(";") && line.contains(" ")) return "YAML"
            }
        } catch (_: Exception) {}

        // 3.4) MIME (как подсказка)
        try {
            val mime = java.nio.file.Files.probeContentType(filePath)
            if (!mime.isNullOrBlank()) {
                return when {
                    mime.contains("json") -> "JSON"
                    mime.contains("xml") -> "XML"
                    mime.contains("yaml") || mime.contains("yml") -> "YAML"
                    mime.contains("javascript") -> "JavaScript"
                    mime.contains("x-python") || mime.contains("python") -> "Python"
                    mime.contains("x-shellscript") || mime.contains("shell") -> "Shell"
                    mime.contains("html") -> "HTML"
                    mime.contains("css") -> "CSS"
                    else -> null
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun readFirstBytes(filePath: Path, n: Int): ByteArray? {
        return try {
            java.nio.file.Files.newInputStream(filePath).use { input ->
                val buf = ByteArray(n)
                val read = input.read(buf)
                if (read <= 0) null else buf.copyOf(read)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFirstLine(filePath: Path): String? {
        return try {
            java.nio.file.Files.newBufferedReader(filePath).use { it.readLine() }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFirstNonEmptyTrimmed(filePath: Path): String? {
        return try {
            java.nio.file.Files.newBufferedReader(filePath).use { br ->
                var line: String?
                do {
                    line = br.readLine() ?: return null
                } while (line.trim().isEmpty())
                line.trim()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Анализирует содержимое кода
     */
    private fun analyzeCodeContent(content: String, language: String): CodeMetrics {
        val lines = content.lines()
        var totalLines = lines.size
        var blankLines = 0
        var commentLines = 0
        var codeLines = 0

        when (language) {
            "Java", "Kotlin", "Scala", "C", "C++", "C#", "Go", "Rust", "Swift", "JavaScript", "TypeScript" -> {
                analyzeCLikeLanguages(lines).let { metrics ->
                    blankLines = metrics.blankLines
                    commentLines = metrics.commentLines
                    codeLines = metrics.codeLines
                }
            }
            "Python" -> {
                analyzePythonLanguage(lines).let { metrics ->
                    blankLines = metrics.blankLines
                    commentLines = metrics.commentLines
                    codeLines = metrics.codeLines
                }
            }
            "Shell", "Batch", "PowerShell" -> {
                analyzeScriptLanguages(lines).let { metrics ->
                    blankLines = metrics.blankLines
                    commentLines = metrics.commentLines
                    codeLines = metrics.codeLines
                }
            }
            "HTML", "XML", "Markdown" -> {
                analyzeMarkupLanguages(lines).let { metrics ->
                    blankLines = metrics.blankLines
                    commentLines = metrics.commentLines
                    codeLines = metrics.codeLines
                }
            }
            "JSON", "YAML", "TOML", "INI", "Properties" -> {
                analyzeDataLanguages(lines).let { metrics ->
                    blankLines = metrics.blankLines
                    commentLines = metrics.commentLines
                    codeLines = metrics.codeLines
                }
            }
            else -> {
                // Базовый анализ для неизвестных языков
                lines.forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() -> blankLines++
                        trimmed.startsWith("#") || trimmed.startsWith("//") ||
                        trimmed.startsWith("/*") || trimmed.startsWith("*") ||
                        trimmed.startsWith("<!--") -> commentLines++
                        else -> codeLines++
                    }
                }
            }
        }

        return CodeMetrics(
            totalLines = totalLines,
            codeLines = codeLines,
            commentLines = commentLines,
            blankLines = blankLines,
            language = language,
            complexity = determineComplexity(codeLines)
        )
    }

    /**
     * Анализ C-подобных языков
     */
    private fun analyzeCLikeLanguages(lines: List<String>): CodeMetrics {
        var blankLines = 0
        var commentLines = 0
        var codeLines = 0
        var inBlockComment = false

        lines.forEach { line ->
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> blankLines++
                inBlockComment -> {
                    commentLines++
                    if (trimmed.contains("*/")) {
                        inBlockComment = false
                    }
                }
                trimmed.startsWith("/*") -> {
                    commentLines++
                    inBlockComment = !trimmed.contains("*/")
                }
                trimmed.startsWith("//") -> commentLines++
                trimmed.startsWith("/**") -> commentLines++
                trimmed.startsWith("*") && !trimmed.startsWith("* ") -> commentLines++
                else -> {
                    // Проверяем на однострочные комментарии в строке с кодом
                    val codePart = if (trimmed.contains("//")) {
                        trimmed.substringBefore("//")
                    } else {
                        trimmed
                    }

                    if (codePart.isNotEmpty()) {
                        codeLines++
                    }
                }
            }
        }

        return CodeMetrics(0, codeLines, commentLines, blankLines, "", FileComplexity.LOW)
    }

    /**
     * Анализ Python
     */
    private fun analyzePythonLanguage(lines: List<String>): CodeMetrics {
        var blankLines = 0
        var commentLines = 0
        var codeLines = 0
        var inMultilineString = false

        lines.forEach { line ->
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> blankLines++
                inMultilineString -> {
                    commentLines++ // Считаем многострочные строки как комментарии
                    if (trimmed.contains("\"\"\"") || trimmed.contains("'''")) {
                        inMultilineString = false
                    }
                }
                trimmed.startsWith("#") -> commentLines++
                trimmed.contains("\"\"\"") || trimmed.contains("'''") -> {
                    commentLines++
                    inMultilineString = !(trimmed.count { it == '"' } >= 3 || trimmed.count { it == '\'' } >= 3)
                }
                else -> codeLines++
            }
        }

        return CodeMetrics(0, codeLines, commentLines, blankLines, "", FileComplexity.LOW)
    }

    /**
     * Анализ скриптовых языков
     */
    private fun analyzeScriptLanguages(lines: List<String>): CodeMetrics {
        var blankLines = 0
        var commentLines = 0
        var codeLines = 0

        lines.forEach { line ->
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> blankLines++
                trimmed.startsWith("#") -> commentLines++
                trimmed.startsWith("REM") -> commentLines++ // Для Batch
                trimmed.startsWith("//") -> commentLines++ // Для PowerShell
                else -> codeLines++
            }
        }

        return CodeMetrics(0, codeLines, commentLines, blankLines, "", FileComplexity.LOW)
    }

    /**
     * Анализ разметочных языков
     */
    private fun analyzeMarkupLanguages(lines: List<String>): CodeMetrics {
        var blankLines = 0
        var commentLines = 0
        var codeLines = 0

        lines.forEach { line ->
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> blankLines++
                trimmed.startsWith("<!--") -> commentLines++
                trimmed.contains("<!--") -> {
                    commentLines++
                    codeLines++
                }
                else -> codeLines++
            }
        }

        return CodeMetrics(0, codeLines, commentLines, blankLines, "", FileComplexity.LOW)
    }

    /**
     * Анализ языков данных
     */
    private fun analyzeDataLanguages(lines: List<String>): CodeMetrics {
        var blankLines = 0
        var commentLines = 0
        var codeLines = 0

        lines.forEach { line ->
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> blankLines++
                trimmed.startsWith("#") -> commentLines++ // YAML, TOML
                trimmed.startsWith("//") -> commentLines++ // Некоторые конфиги
                trimmed.startsWith(";") -> commentLines++   // INI
                else -> codeLines++
            }
        }

        return CodeMetrics(0, codeLines, commentLines, blankLines, "", FileComplexity.LOW)
    }

    /**
     * Определяет сложность файла по количеству строк кода
     */
    private fun determineComplexity(codeLines: Int): FileComplexity {
        return when {
            codeLines < 100 -> FileComplexity.LOW
            codeLines < 500 -> FileComplexity.MEDIUM
            codeLines < 1000 -> FileComplexity.HIGH
            else -> FileComplexity.VERY_HIGH
        }
    }

    /**
     * Получает категорию языка (для группировки в статистике)
     */
    fun getLanguageCategory(language: String): String {
        return when (language) {
            "Java", "Kotlin", "Scala", "Groovy", "Clojure" -> "JVM Languages"
            "Python" -> "Python"
            "JavaScript", "TypeScript", "JSX", "Vue" -> "JavaScript/TypeScript"
            "C", "C++", "C#", "Go", "Rust", "Swift", "D" -> "System Languages"
            "Ruby", "PHP", "Perl", "Elixir", "Erlang" -> "Dynamic Languages"
            "HTML", "CSS", "SCSS", "Less", "Stylus" -> "Web Technologies"
            "SQL", "JSON", "XML", "YAML", "TOML" -> "Data/Config"
            "Shell", "Batch", "PowerShell" -> "Scripts"
            "Markdown", "Text" -> "Documentation"
            else -> "Other"
        }
    }
}