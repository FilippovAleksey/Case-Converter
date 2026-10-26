package me.filippov.caseconverter

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.SmartPointerManager
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.usageView.UsageInfo
import java.util.LinkedHashMap

/** Один пункт плана: PSI-объявление и рассчитанное новое имя. */
data class RenameTarget(val element: PsiNamedElement, val newName: String)

/**
 * Один RenameProcessor для всего блока: общие Preview, проверки и Undo.
 * Простые совпадения в строках и комментариях не включаем в поиск.
 */
class CaseRenameProcessor(
    project: Project,
    private val targets: List<RenameTarget>,
    private val style: CaseConverter.Style,
) : RenameProcessor(project, targets.first().element, targets.first().newName, false, false) {
    // Сохраняем прежний конструктор для операции над одним символом.
    constructor(project: Project, element: PsiNamedElement, newName: String, style: CaseConverter.Style) :
        this(project, listOf(RenameTarget(element, newName)), style)

    private val tracked = targets.map {
        Triple(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it.element),
            it.newName, it.element.name.orEmpty().length)
    }

    init {
        setCommandName("Case Converter: " + targets.size + " символов → " +
            if (style == CaseConverter.Style.SNAKE) "snake_case" else "camelCase")
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: LinkedHashMap<PsiElement, String>) {
        // Все цели должны быть известны языковым процессорам до поиска ссылок.
        targets.forEach { allRenames[it.element] = it.newName }
        targets.forEach { super.prepareRenaming(it.element, it.newName, allRenames) }
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        super.performRefactoring(usages)
        val settings = PluginSettings.getInstance().state
        val renamed = tracked.filter { it.first.element?.name == it.second }
        if (settings.collectStatistics && renamed.isNotEmpty()) {
            // Блок — одна операция; отдельный счётчик содержит число изменённых символов.
            StatisticsService.getInstance().record(style, renamed.size, renamed.sumOf { it.third }, settings.historyLimit)
            StatisticsService.notifyChanged()
        }
    }
}

