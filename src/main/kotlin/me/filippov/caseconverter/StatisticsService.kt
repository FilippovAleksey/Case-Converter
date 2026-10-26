package me.filippov.caseconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import java.time.Instant

/** Событие обновления: окно получает уведомление без постоянного опроса таймером. */
fun interface StatisticsListener {
    fun statisticsChanged()
}

/**
 * Храним только числа, время и направление. Текст кода и пути файлов не сохраняем.
 * Итоги — за всё время с последнего сброса, история — ограниченный список операций.
 * synchronized защищает снимки от одновременного чтения при сохранении настроек IDE.
 */
@Service(Service.Level.APP)
@State(name = "CaseConverterStatistics", storages = [Storage("case-converter-statistics.xml")])
class StatisticsService : PersistentStateComponent<StatisticsService.Data> {
    data class Entry(
        var timestamp: Long = 0,
        var style: String = "",
        var identifiers: Int = 0,
        var characters: Int = 0,
    )
    data class Data(
        var snakeOperations: Long = 0,
        var camelOperations: Long = 0,
        var changedIdentifiers: Long = 0,
        var processedCharacters: Long = 0,
        var history: MutableList<Entry> = mutableListOf(),
    )

    private var current = Data()
    @Synchronized override fun getState(): Data = current.copy(
        history = current.history.map { it.copy() }.toMutableList()
    )
    @Synchronized override fun loadState(state: Data) {
        current = state.copy(history = state.history.takeLast(PluginSettings.MAX_HISTORY)
            .map { it.copy() }.toMutableList())
    }

    @Synchronized
    fun record(style: CaseConverter.Style, identifiers: Int, characters: Int, historyLimit: Int) {
        // Не считаем попытки без изменений; выключенный сбор проверяется процессором Rename.
        if (identifiers <= 0) return
        when (style) {
            CaseConverter.Style.SNAKE -> current.snakeOperations++
            CaseConverter.Style.CAMEL -> current.camelOperations++
        }
        current.changedIdentifiers += identifiers
        current.processedCharacters += characters
        current.history.add(Entry(Instant.now().toEpochMilli(), style.name, identifiers, characters))
        trimHistory(historyLimit)
    }

    @Synchronized fun trimHistory(limit: Int) {
        current.history = current.history.takeLast(
            limit.coerceIn(PluginSettings.MIN_HISTORY, PluginSettings.MAX_HISTORY)
        ).toMutableList()
    }

    @Synchronized fun clear() { current = Data() }

    companion object {
        val TOPIC: Topic<StatisticsListener> =
            Topic.create("Case Converter statistics", StatisticsListener::class.java)
        fun getInstance(): StatisticsService =
            ApplicationManager.getApplication().getService(StatisticsService::class.java)

        // Вызываем после записи/сброса и изменения настроек, когда данные уже согласованы.
        fun notifyChanged() {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).statisticsChanged()
        }
    }
}

