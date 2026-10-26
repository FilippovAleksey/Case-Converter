package me.filippov.caseconverter

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Интеграционные тесты запускают настоящие PSI, поиск ссылок и RenameProcessor.
 * В unit test mode платформа выполняет рефакторинг синхронно без окна Preview.
 * Это проверяет реальное изменение ссылок, а не имитацию строковой замены.
 */
class RenameIntegrationTest : LightJavaCodeInsightFixtureTestCase() {
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

    fun testFieldReferencesAcrossFilesAndUnrelatedNames() {
        val declaration = myFixture.addClass("public class Model { public int user_name = 1; }")
        val usage = myFixture.configureByText("Usage.java", """
            class Usage {
                int read(Model model) {
                    int user_name = 2;
                    String text = "user_name";
                    // model.user_name is a comment
                    return model.user_name + user_name;
                }
            }
        """.trimIndent())
        val field = declaration.findFieldByName("user_name", false)!!
        CaseRenameProcessor(project, field, "userName", CaseConverter.Style.CAMEL).run()
        assertTrue(declaration.text.contains("int userName"))
        assertTrue(usage.text.contains("model.userName + user_name"))
        assertTrue(usage.text.contains("int user_name = 2"))
        assertTrue(usage.text.contains("\"user_name\""))
        assertTrue(usage.text.contains("// model.user_name"))
        assertEquals(1L, StatisticsService.getInstance().state.camelOperations)
    }

    fun testMethodDeclarationAndCall() {
        val declaration = myFixture.addClass("public class Service { public void readFile() {} }")
        val usage = myFixture.configureByText("Caller.java", "class Caller { void run() { new Service().readFile(); } }")
        CaseRenameProcessor(project, declaration.findMethodsByName("readFile", false).single(),
            "read_file", CaseConverter.Style.SNAKE).run()
        assertTrue(declaration.text.contains("void read_file()"))
        assertTrue(usage.text.contains(".read_file()"))
    }

    fun testActionFromSelectedReference() {
        val declaration = myFixture.addClass("public class ActionModel { public int user_name; }")
        val usage = myFixture.configureByText("ActionUsage.java",
            "class ActionUsage { int read(ActionModel model) { return model.<selection>user_name</selection>; } }")
        myFixture.testAction(ToCamelCaseAction())
        assertTrue(declaration.text.contains("int userName"))
        assertTrue(usage.text.contains("model.userName"))
        assertEquals(1L, StatisticsService.getInstance().state.camelOperations)
    }

    fun testActionFromCaretOnDeclaration() {
        val file = myFixture.configureByText("CaretSample.java",
            "class CaretSample { int user<caret>Name; int read() { return userName; } }")
        myFixture.testAction(ToSnakeCaseAction())
        assertTrue(file.text.contains("int user_name"))
        assertTrue(file.text.contains("return user_name"))
    }

    // Диалоги заменяем тестовым обработчиком, но само действие исполняем полностью.
    private fun assertRejectedWithoutChanges(fileName: String, text: String, action: RenameCaseAction) {
        val file = myFixture.configureByText(fileName, text)
        val before = file.text
        var messages = 0
        val previous = TestDialogManager.setTestDialog {
            messages++
            Messages.OK
        }
        try {
            myFixture.testAction(action)
            assertEquals(before, file.text)
            assertEquals(1, messages)
            assertEquals(0L, StatisticsService.getInstance().state.changedIdentifiers)
        } finally { TestDialogManager.setTestDialog(previous) }
    }

    fun testAlreadyConvertedNameIsNotCounted() {
        assertRejectedWithoutChanges("Same.java", "class Same { int user_<caret>name; }", ToSnakeCaseAction())
    }

    fun testCodeBlockRenamesAllFieldsAndExternalReferencesWithOneUndo() {
        val external = myFixture.addFileToProject("UseBlock.java",
            "class UseBlock { int read(Block b) { return b.first_name + b.last_name; } }")
        val file = myFixture.configureByText("Block.java",
            "class Block { <selection>int first_name; int last_name;</selection> }")
        myFixture.testAction(ToCamelCaseAction())
        assertTrue(file.text.contains("int firstName; int lastName;"))
        assertTrue(external.text.contains("b.firstName + b.lastName"))
        assertEquals(1L, StatisticsService.getInstance().state.camelOperations)
        assertEquals(2L, StatisticsService.getInstance().state.changedIdentifiers)
        val editor = FileEditorManager.getInstance(project).selectedEditor
        // Для Undo нескольких файлов IDE спрашивает подтверждение. В тесте отвечаем OK.
        val previous = TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK)
        try {
            UndoManager.getInstance(project).undo(editor)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            assertTrue(file.text.contains("int first_name; int last_name;"))
            assertTrue(external.text.contains("b.first_name + b.last_name"))
            UndoManager.getInstance(project).redo(editor)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            assertTrue(file.text.contains("int firstName; int lastName;"))
            assertTrue(external.text.contains("b.firstName + b.lastName"))
            assertEquals(1L, StatisticsService.getInstance().state.camelOperations)
        } finally { TestDialogManager.setTestDialog(previous) }
    }

    fun testBatchConflictWithUnselectedFieldDoesNotChangeAnyTarget() {
        val file = myFixture.configureByText("BatchConflict.java",
            "class BatchConflict { <selection>int first_name; int last_name;</selection> int lastName; }")
        val before = file.text
        try {
            myFixture.testAction(ToCamelCaseAction())
            fail("Ожидался конфликт с полем за пределами выделения")
        } catch (_: BaseRefactoringProcessor.ConflictsInTestsException) {
            assertEquals(before, file.text)
            assertEquals(0L, StatisticsService.getInstance().state.changedIdentifiers)
        }
    }

    fun testInvalidNameAbortsEntireBatch() {
        assertRejectedWithoutChanges("InvalidBatch.java",
            "class InvalidBatch { <selection>int userName; int Class;</selection> }", ToSnakeCaseAction())
    }

    fun testKeywordIsRejected() {
        assertRejectedWithoutChanges("Keyword.java", "class Keyword { int Cl<caret>ass; }", ToSnakeCaseAction())
    }

    fun testPlainTextDoesNotFallBackToTextReplacement() {
        assertRejectedWithoutChanges("notes.txt", "user_<caret>name", ToCamelCaseAction())
    }

    fun testConflictDoesNotChangeCodeOrStatistics() {
        val declaration = myFixture.addClass(
            "public class Conflict { public int user_name; public int userName; }")
        val before = declaration.text
        try {
            CaseRenameProcessor(project, declaration.findFieldByName("user_name", false)!!,
                "userName", CaseConverter.Style.CAMEL).run()
            fail("Ожидался конфликт двух полей с одним именем")
        } catch (_: BaseRefactoringProcessor.ConflictsInTestsException) {
            assertEquals(before, declaration.text)
            assertEquals(0L, StatisticsService.getInstance().state.camelOperations)
        }
    }

    fun testDisabledStatisticsStillRenames() {
        PluginSettings.getInstance().loadState(PluginSettings.Data(collectStatistics = false))
        val declaration = myFixture.addClass("public class Quiet { public int user_name; }")
        CaseRenameProcessor(project, declaration.findFieldByName("user_name", false)!!,
            "userName", CaseConverter.Style.CAMEL).run()
        assertTrue(declaration.text.contains("int userName"))
        assertEquals(0L, StatisticsService.getInstance().state.camelOperations)
    }

    fun testUndoRedoRestoresDeclarationAndReference() {
        val file = myFixture.configureByText("Sample.java",
            "class Sample { int user_name; int read() { return user_name; } }") as PsiJavaFile
        val field = file.classes.single().fields.single()
        CaseRenameProcessor(project, field, "userName", CaseConverter.Style.CAMEL).run()
        val fileEditor = FileEditorManager.getInstance(project).selectedEditor
        val undo = UndoManager.getInstance(project)
        assertTrue(undo.isUndoAvailable(fileEditor))
        undo.undo(fileEditor)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(file.text.contains("int user_name;"))
        assertTrue(file.text.contains("return user_name;"))
        undo.redo(fileEditor)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(file.text.contains("int userName;"))
        assertTrue(file.text.contains("return userName;"))
        // История учитывает действия пользователя, а не текущее состояние текста.
        assertEquals(1L, StatisticsService.getInstance().state.camelOperations)
    }
}

