package me.filippov.caseconverter

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameUtil

/**
 * Контроллер определяет PSI-символы и собирает единый план переименования.
 * Без выделения берём символ под курсором, с выделением — все полные имена внутри.
 * Индексы нужны для разрешения ссылок, поэтому при индексации действие отключено.
 */
abstract class RenameCaseAction : AnAction() {
    protected abstract val targetStyle: CaseConverter.Style
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val project = event.project
        event.presentation.isEnabled = project != null && !DumbService.isDumb(project) &&
            editor != null && !editor.isViewer && editor.document.isWritable &&
            editor.caretModel.caretCount == 1
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        if (editor.isViewer || editor.caretModel.caretCount != 1 || DumbService.isDumb(project)) return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        fun explain(message: String) = Messages.showInfoMessage(project, message, "Case Converter")
        if (file == null || file.language.id == "TEXT") {
            explain("IDE распознаёт файл как обычный текст. Включите поддержку языка " +
                "(для .py — Python), проверьте тип файла. В тестовой IDE перезапустите runIde.")
            return
        }
        val flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        fun targetAt(offset: Int) = TargetElementUtil.getInstance()
            .findTargetElement(editor, flags, offset) as? PsiNamedElement
        val candidates = mutableListOf<PsiNamedElement>()
        fun addCandidate(target: PsiNamedElement?) {
            if (target == null || target is PsiFile || target is PsiDirectory) return
            // Объявление и несколько ссылок должны дать одну цель, а не несколько команд.
            if (candidates.none { file.manager.areElementsEquivalent(it, target) }) candidates.add(target)
        }
        if (editor.selectionModel.hasSelection()) {
            val start = editor.selectionModel.selectionStart
            val end = editor.selectionModel.selectionEnd
            var leaf = file.findElementAt(start)
            // Идём по листьям PSI только внутри выделения. Не сканируем файл регуляркой:
            // строка/комментарий не являются токеном имени объявленного символа.
            while (leaf != null && leaf.textRange.startOffset < end) {
                val range = leaf.textRange
                if (range.startOffset >= start && range.endOffset <= end &&
                    leaf.text.firstOrNull()?.let { Character.isJavaIdentifierStart(it) } == true) {
                    val target = targetAt(range.startOffset)
                    if (target?.name == leaf.text) addCandidate(target)
                }
                leaf = PsiTreeUtil.nextLeaf(leaf)
            }
        } else {
            addCandidate(targetAt(editor.caretModel.offset))
        }
        if (candidates.isEmpty()) {
            explain("Поставьте курсор на имя или выделите блок с полными именами переменных/функций. " +
                "IDE должна поддерживать Rename для этого языка.")
            return
        }

        val settings = PluginSettings.getInstance().state
        val options = CaseConverter.Options(settings.preserveEdgeUnderscores, settings.splitAcronyms)
        val plan = mutableListOf<RenameTarget>()

        // Сначала собираем и проверяем весь план. До последнего шага код не меняется.
        // Языковой процессор вправе предложить базовый метод вместо переопределения.
        fun prepare(index: Int) {
            if (index == candidates.size) {
                if (plan.isEmpty()) {
                    explain("Имена уже соответствуют выбранному стилю.")
                    return
                }
                // Консервативная проверка будущих коллизий: не допускаем два разных
                // символа с одинаковым новым именем даже в разных областях видимости.
                // Такие символы можно переименовать отдельно после проверки IDE.
                if (plan.groupBy { it.newName }.any { it.value.size > 1 }) {
                    explain("Разные символы получат одинаковое имя. Переименуйте их отдельно. Код не изменён.")
                    return
                }
                CaseRenameProcessor(project, plan, targetStyle).apply { setPreviewUsages(true) }.run()
                return
            }
            val candidate = candidates[index]
            // Не трогаем уже подходящие имена, включая встроенные print, len и т. п.
            if (candidate.name?.let { CaseConverter.convert(it, targetStyle, options) == it } == true) {
                prepare(index + 1)
                return
            }
            RenamePsiElementProcessor.forElement(candidate).substituteElementToRename(candidate, editor,
                object : Pass<PsiElement>() {
                    override fun pass(element: PsiElement) {
                        val named = element as? PsiNamedElement ?: run {
                            explain("IDE не предоставила именованный символ для Rename. Код не изменён.")
                            return
                        }
                        val oldName = named.name ?: return
                        val newName = CaseConverter.convert(oldName, targetStyle, options)
                        val error = PsiElementRenameHandler.getRenameErrorMessage(project, editor, element)
                        if (error != null) { explain(error); return }
                        if (!RenameUtil.isValidName(project, element, newName)) {
                            explain("Имя «$newName» недопустимо для этого языка. Код не изменён.")
                            return
                        }
                        if (oldName != newName && plan.none { file.manager.areElementsEquivalent(it.element, named) })
                            plan.add(RenameTarget(named, newName))
                        prepare(index + 1)
                    }
                })
        }
        prepare(0)
    }
}

class ToSnakeCaseAction : RenameCaseAction() {
    override val targetStyle get() = CaseConverter.Style.SNAKE
}
class ToCamelCaseAction : RenameCaseAction() {
    override val targetStyle get() = CaseConverter.Style.CAMEL
}

