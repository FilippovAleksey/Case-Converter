package me.filippov.caseconverter

/**
 * Чистая логика, не знающая об IDE, файлах и кнопках.
 * object — единственный экземпляр класса; его методы вызываются через CaseConverter.
 * Благодаря этому алгоритм можно проверять обычными unit-тестами без запуска IDE.
 */
object CaseConverter {
    private val acronymBoundary = Regex("(\\p{Lu})(\\p{Lu}\\p{Ll})")
    private val wordBoundary = Regex("([\\p{Ll}\\p{N}])(\\p{Lu})")

    enum class Style { SNAKE, CAMEL }

    // data class автоматически получает equals, copy и toString.
    data class Options(val preserveEdgeUnderscores: Boolean = true, val splitAcronyms: Boolean = true)
    // Принимаем одно имя из PSI. Метод не применяется к блоку исходного кода.
    fun convert(value: String, style: Style, options: Options = Options()): String {
        val core = value.trim('_')
        // Одинокие подчёркивания — тоже допустимые токены, но менять их незачем.
        if (core.isEmpty()) return value
        val prefix = if (options.preserveEdgeUnderscores) value.takeWhile { it == '_' } else ""
        val suffix = if (options.preserveEdgeUnderscores) value.takeLastWhile { it == '_' } else ""

        // HTTPServer → HTTP_Server, затем userName → user_Name.
        // Порядок правил важен: сначала отделяем последнее слово от аббревиатуры.
        val expanded = if (options.splitAcronyms) acronymBoundary.replace(core, "$1_$2") else core
        val words = wordBoundary.replace(expanded, "$1_$2").split('_').filter { it.isNotEmpty() }
        // lowercase() в Kotlin не зависит от локали ОС (важно для турецкой I).
        val lower = words.map { it.lowercase() }
        val converted = when (style) {
            Style.SNAKE -> lower.joinToString("_")
            Style.CAMEL -> lower.first() + lower.drop(1).joinToString("") {
                it.replaceFirstChar { character -> character.titlecase() }
            }
        }
        return prefix + converted + suffix
    }
}

