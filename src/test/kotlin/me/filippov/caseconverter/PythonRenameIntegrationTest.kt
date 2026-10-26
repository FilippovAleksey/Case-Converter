package me.filippov.caseconverter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Проверяем Python через реальный языковой плагин, без прямых импортов его API. */
class PythonRenameIntegrationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        PluginSettings.getInstance().loadState(PluginSettings.Data())
        StatisticsService.getInstance().clear()
    }

    override fun tearDown() {
        try {
            PluginSettings.getInstance().loadState(PluginSettings.Data())
            StatisticsService.getInstance().clear()
        } finally { super.tearDown() }
    }

    fun testUserExampleWithoutUsages() {
        val file = myFixture.configureByText("test.py", "camel<caret>Snake = 0\n")
        assertEquals("Python", file.language.id) // Ловит отсутствие Python в sandbox.
        myFixture.testAction(ToSnakeCaseAction())
        assertEquals("camel_snake = 0\n", file.text)
        assertEquals(1L, StatisticsService.getInstance().state.snakeOperations)
    }

    fun testRenameUpdatesUsagesButNotIndependentVariableOrString() {
        val file = myFixture.configureByText("test.py", """
            <selection>camelSnake</selection> = 0
            print(camelSnake)
            text = "camelSnake"
            def independent():
                camelSnake = 2
                return camelSnake
        """.trimIndent())
        myFixture.testAction(ToSnakeCaseAction())
        assertTrue(file.text.startsWith("camel_snake = 0"))
        assertTrue(file.text.contains("print(camel_snake)"))
        assertTrue(file.text.contains("text = \"camelSnake\""))
        assertTrue(file.text.contains("    camelSnake = 2"))
        assertTrue(file.text.contains("    return camelSnake"))
    }

    fun testCamelCaseFromReference() {
        val file = myFixture.configureByText("test.py", "camel_snake = 0\nprint(camel_<caret>snake)\n")
        myFixture.testAction(ToCamelCaseAction())
        assertEquals("camelSnake = 0\nprint(camelSnake)\n", file.text)
    }

    fun testSevenSelectedAssignmentsFromUserExample() {
        val names = listOf("camelSnake", "camelSna12312ke", "camelSna85878ke", "camelSna345ke",
            "camelSna333ke", "camelSnak7879e", "camelSn7777ake")
        val expected = listOf("camel_snake", "camel_sna12312ke", "camel_sna85878ke", "camel_sna345ke",
            "camel_sna333ke", "camel_snak7879e", "camel_sn7777ake")
        val declarations = names.joinToString("\n") { "$it = 0" }
        val references = names.joinToString(", ")
        val file = myFixture.configureByText("batch.py", "<selection>$declarations</selection>\nprint($references)\n")
        myFixture.testAction(ToSnakeCaseAction())
        assertEquals(expected.joinToString("\n") { "$it = 0" } +
            "\nprint(" + expected.joinToString(", ") + ")\n", file.text)
        val data = StatisticsService.getInstance().state
        assertEquals(1L, data.snakeOperations)
        assertEquals(7L, data.changedIdentifiers)
        assertEquals(7, data.history.single().identifiers)
        assertEquals(names.sumOf { it.length }.toLong(), data.processedCharacters)
    }

    fun testReferencesDeduplicateAndStringsAndCommentsAreSkipped() {
        val file = myFixture.configureByText("references.py", """
            first_name = 0
            last_name = 1
            <selection>print(first_name, first_name, last_name)
            text = "first_name last_name"
            # first_name last_name</selection>
            print(first_name, last_name)
        """.trimIndent())
        myFixture.testAction(ToCamelCaseAction())
        assertTrue(file.text.startsWith("firstName = 0\nlastName = 1"))
        assertTrue(file.text.contains("print(firstName, firstName, lastName)"))
        assertTrue(file.text.contains("print(firstName, lastName)"))
        assertTrue(file.text.contains("\"first_name last_name\""))
        assertTrue(file.text.contains("# first_name last_name"))
        assertEquals(2L, StatisticsService.getInstance().state.changedIdentifiers)
    }

    fun testPartiallySelectedNameIsNotIncludedInBatch() {
        val file = myFixture.configureByText("partial.py",
            "camel<selection>Snake = 0\notherName = 1</selection>\nprint(camelSnake, otherName)")
        myFixture.testAction(ToSnakeCaseAction())
        assertEquals("camelSnake = 0\nother_name = 1\nprint(camelSnake, other_name)", file.text)
        assertEquals(1L, StatisticsService.getInstance().state.changedIdentifiers)
    }

    fun testFutureDuplicateNamesAbortBatch() {
        val file = myFixture.configureByText("collision.py",
            "<selection>camelSnake = 0\nCamelSnake = 1</selection>\nprint(camelSnake, CamelSnake)")
        val before = file.text
        var messages = 0
        val previous = com.intellij.openapi.ui.TestDialogManager.setTestDialog {
            messages++
            com.intellij.openapi.ui.Messages.OK
        }
        try {
            myFixture.testAction(ToSnakeCaseAction())
            assertEquals(before, file.text)
            assertEquals(1, messages)
            assertEquals(0L, StatisticsService.getInstance().state.changedIdentifiers)
        } finally { com.intellij.openapi.ui.TestDialogManager.setTestDialog(previous) }
    }
}
