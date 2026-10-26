package me.filippov.caseconverter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Маленькие тесты алгоритма: вход → ожидаемое имя, без редактора IDE. */
class CaseConverterTest {
    private val snake = CaseConverter.Style.SNAKE
    private val camel = CaseConverter.Style.CAMEL

    @Test fun ordinaryNames() {
        assertEquals("user_name", CaseConverter.convert("userName", snake))
        assertEquals("userName", CaseConverter.convert("user_name", camel))
        assertEquals("readFile", CaseConverter.convert("ReadFile", camel))
    }
    @Test fun acronyms() {
        assertEquals("http_server_url", CaseConverter.convert("HTTPServerURL", snake))
        assertEquals("httpServerUrl", CaseConverter.convert("HTTPServerURL", camel))
        assertEquals("url", CaseConverter.convert("URL", snake))
    }
    @Test fun disabledAcronymRule() {
        val options = CaseConverter.Options(splitAcronyms = false)
        assertEquals("httpserver", CaseConverter.convert("HTTPServer", snake, options))
        assertEquals("httpserver", CaseConverter.convert("HTTPServer", camel, options))
    }
    @Test fun edgeUnderscores() {
        assertEquals("__userName__", CaseConverter.convert("__user_name__", camel))
        assertEquals("_user_name_", CaseConverter.convert("_userName_", snake))
        assertEquals("userName", CaseConverter.convert("__user_name__", camel,
            CaseConverter.Options(preserveEdgeUnderscores = false)))
    }
    @Test fun emptyAndUnderscoresOnly() {
        for (style in CaseConverter.Style.entries) {
            assertEquals("", CaseConverter.convert("", style))
            assertEquals("___", CaseConverter.convert("___", style))
        }
    }
    @Test fun repeatedSeparatorsAndDigits() {
        assertEquals("userName2", CaseConverter.convert("user__name2", camel))
        assertEquals("version2_value", CaseConverter.convert("version2Value", snake))
        assertEquals("get2FaToken", CaseConverter.convert("get_2_fa_token", camel))
    }
    @Test fun unicodeNames() {
        assertEquals("имя_пользователя", CaseConverter.convert("имяПользователя", snake))
        assertEquals("имяПользователя", CaseConverter.convert("имя_пользователя", camel))
    }
    @Test fun localeDoesNotChangeConversion() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("item_id", CaseConverter.convert("ItemID", snake))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun repeatedConversionIsStable() {
        for (style in CaseConverter.Style.entries) {
            for (name in listOf("userName", "HTTPServerURL", "__user_name__", "version2Value")) {
                val once = CaseConverter.convert(name, style)
                assertEquals(once, CaseConverter.convert(once, style))
            }
        }
    }
}

