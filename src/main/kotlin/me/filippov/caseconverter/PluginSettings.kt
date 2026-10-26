package me.filippov.caseconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application service общий для всех проектов. IDE создаёт его по запросу.
 * @State задаёт имя компонента и XML-файл в каталоге настроек IDE.
 * Все пользовательские параметры этого класса представлены на странице Settings.
 */
@Service(Service.Level.APP)
@State(name = "CaseConverterSettings", storages = [Storage("case-converter-settings.xml")])
class PluginSettings : PersistentStateComponent<PluginSettings.Data> {
    data class Data(
        var preserveEdgeUnderscores: Boolean = true,
        var splitAcronyms: Boolean = true,
        var collectStatistics: Boolean = true,
        var historyLimit: Int = 100,
    )

    private var current = Data()

    // Копии не позволяют UI случайно изменить настройки до нажатия Apply.
    @Synchronized override fun getState(): Data = current.copy()
    @Synchronized override fun loadState(state: Data) {
        current = state.copy(historyLimit = state.historyLimit.coerceIn(MIN_HISTORY, MAX_HISTORY))
    }

    companion object {
        const val MIN_HISTORY = 10
        const val MAX_HISTORY = 1000
        fun getInstance(): PluginSettings =
            ApplicationManager.getApplication().getService(PluginSettings::class.java)
    }
}

