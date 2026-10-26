package me.filippov.caseconverter

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Configurable — контракт страницы Settings: создать UI, обнаружить изменения,
 * применить, отменить несохранённые изменения и освободить ссылки на Swing-компоненты.
 */
class SettingsConfigurable : Configurable {
    private var form: Form? = null
    private class Form {
        val preserve = JBCheckBox("Сохранять крайние подчёркивания: __user_name__ → __userName__")
        val acronyms = JBCheckBox("Выделять аббревиатуры: HTTPServer → http_server / httpServer")
        val statistics = JBCheckBox("Собирать локальную статистику переименований")
        val limit = JSpinner(SpinnerNumberModel(100, PluginSettings.MIN_HISTORY, PluginSettings.MAX_HISTORY, 10))
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(JPanel(GridLayout(0, 1, 0, 10)).apply {
                add(preserve)
                add(acronyms)
                add(statistics)
                add(JPanel(BorderLayout(12, 0)).apply {
                    add(JBLabel("Максимум записей истории (10–1000):"), BorderLayout.WEST)
                    add(limit, BorderLayout.CENTER)
                })
                add(JBLabel("<html>Настройки общие для всех проектов.<br>" +
                    "Хранятся только счётчики и время, без имён и исходного кода.<br>" +
                    "Уменьшение лимита удалит старые записи при Apply; итоги сохранятся.<br>" +
                    "Просмотр ссылок и проверка конфликтов выполняются средствами IDE.</html>"))
                add(JButton("Восстановить настройки по умолчанию").apply {
                    addActionListener { display(PluginSettings.Data()) }
                })
            }, BorderLayout.NORTH)
        }

        fun display(data: PluginSettings.Data) {
            preserve.isSelected = data.preserveEdgeUnderscores
            acronyms.isSelected = data.splitAcronyms
            statistics.isSelected = data.collectStatistics
            limit.value = data.historyLimit
        }
        fun read() = PluginSettings.Data(preserve.isSelected, acronyms.isSelected,
            statistics.isSelected, (limit.value as Number).toInt())
    }

    override fun getDisplayName() = "Case Converter"
    override fun createComponent(): JComponent {
        val created = Form()
        form = created
        reset()
        return created.panel
    }
    override fun isModified() = form?.read()?.let { it != PluginSettings.getInstance().state } ?: false
    override fun apply() {
        val view = form ?: return
        // Подтверждаем набранное число, даже если пользователь ещё не покинул поле.
        try { view.limit.commitEdit() } catch (_: java.text.ParseException) {
            throw com.intellij.openapi.options.ConfigurationException("Введите целое число от 10 до 1000.")
        }
        val data = view.read()
        if (data.historyLimit !in PluginSettings.MIN_HISTORY..PluginSettings.MAX_HISTORY)
            throw com.intellij.openapi.options.ConfigurationException("Лимит истории должен быть от 10 до 1000.")
        PluginSettings.getInstance().loadState(data)
        StatisticsService.getInstance().trimHistory(data.historyLimit)
        StatisticsService.notifyChanged()
    }
    override fun reset() { form?.display(PluginSettings.getInstance().state) }
    override fun disposeUIResources() { form = null }
}

