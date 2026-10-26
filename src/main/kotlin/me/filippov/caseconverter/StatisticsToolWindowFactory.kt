package me.filippov.caseconverter

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

/** Фабрика создаёт отдельное представление для каждого проекта, данные общие. */
class StatisticsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val view = StatisticsPanel(project)
        val content = ContentFactory.getInstance().createContent(view, "", false)
        // Подписка освободится вместе с вкладкой, иначе закрытые проекты оставались бы в памяти.
        content.setDisposer(view)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Swing-компоненты меняются только в EDT — потоке интерфейса.
 * Полосы показывают долю операций по направлениям, таблица — ограниченную историю.
 */
class StatisticsPanel(project: Project) : JPanel(BorderLayout(0, 12)), Disposable {
    private val summary = JBLabel()
    private val status = JBLabel()
    private val snake = JProgressBar(0, 100).apply { isStringPainted = true }
    private val camel = JProgressBar(0, 100).apply { isStringPainted = true }
    private val model = object : DefaultTableModel(arrayOf("Время", "Стиль", "Символов", "Сумма длин имён"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JBTable(model)
    private var disposed = false

    init {
        border = JBUI.Borders.empty(12)
        add(JPanel(GridLayout(0, 1, 0, 8)).apply {
            add(JBLabel("Переименования с последнего сброса"))
            add(summary)
            add(status)
            add(snake)
            add(camel)
            add(JBLabel("Последние операции (новые сверху):"))
        }, BorderLayout.NORTH)
        table.emptyText.text = "Переименований пока нет"
        table.setShowGrid(false)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(JPanel().apply {
            add(JButton("Настройки").apply {
                addActionListener { ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable::class.java) }
            })
            add(JButton("Сбросить статистику…").apply {
                addActionListener {
                    if (Messages.showYesNoDialog(project, "Удалить все счётчики и историю? Отменить сброс нельзя.",
                            "Case Converter", Messages.getQuestionIcon()) == Messages.YES) {
                        StatisticsService.getInstance().clear()
                        StatisticsService.notifyChanged()
                    }
                }
            })
        }, BorderLayout.SOUTH)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(StatisticsService.TOPIC, StatisticsListener { refresh() })
        refresh()
    }

    private fun refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { refresh() }
            return
        }
        if (disposed) return
        val data = StatisticsService.getInstance().state
        val total = data.snakeOperations + data.camelOperations
        summary.text = "<html>Операций: $total · Переименовано символов: ${data.changedIdentifiers}<br>" +
            "Сумма длин исходных имён: ${data.processedCharacters} (UTF-16)</html>"
        val settings = PluginSettings.getInstance().state
        status.text = if (settings.collectStatistics) "Сбор включён · история: ${data.history.size}/${settings.historyLimit}"
            else "Сбор выключен · накопленные данные сохранены"
        updateBar(snake, "snake_case", data.snakeOperations, total)
        updateBar(camel, "camelCase", data.camelOperations, total)
        model.rowCount = 0
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        data.history.asReversed().forEach {
            model.addRow(arrayOf(format.format(Instant.ofEpochMilli(it.timestamp)),
                if (it.style == "SNAKE") "snake_case" else "camelCase", it.identifiers, it.characters))
        }
    }

    private fun updateBar(bar: JProgressBar, label: String, count: Long, total: Long) {
        val percent = if (total == 0L) 0 else (100.0 * count / total).toInt()
        bar.value = percent
        bar.string = "$label: $count ($percent%)"
    }

    override fun dispose() { disposed = true }
}

