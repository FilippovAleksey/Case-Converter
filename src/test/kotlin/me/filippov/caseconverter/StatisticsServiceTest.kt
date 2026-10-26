package me.filippov.caseconverter

import org.junit.Assert.*
import org.junit.Test

/** Проверяем контракт хранения отдельно от интерфейса и файлов на диске. */
class StatisticsServiceTest {
    @Test fun countsAndHistoryLimit() {
        val service = StatisticsService()
        repeat(12) { service.record(CaseConverter.Style.SNAKE, 1, 8, 10) }
        service.record(CaseConverter.Style.CAMEL, 1, 5, 10)
        assertEquals(12L, service.state.snakeOperations)
        assertEquals(1L, service.state.camelOperations)
        assertEquals(13L, service.state.changedIdentifiers)
        assertEquals(101L, service.state.processedCharacters)
        assertEquals(10, service.state.history.size)
        assertEquals("CAMEL", service.state.history.last().style)
    }
    @Test fun trimmingDoesNotResetTotals() {
        val service = StatisticsService()
        repeat(20) { service.record(CaseConverter.Style.SNAKE, 1, 4, 100) }
        service.trimHistory(10)
        assertEquals(10, service.state.history.size)
        assertEquals(20L, service.state.snakeOperations)
    }
    @Test fun snapshotsAreIsolatedAndCanBeRestored() {
        val first = StatisticsService()
        first.record(CaseConverter.Style.CAMEL, 1, 4, 100)
        val snapshot = first.state
        val restored = StatisticsService()
        restored.loadState(snapshot)
        snapshot.history.first().style = "modified"
        snapshot.history.clear()
        assertEquals("CAMEL", first.state.history.single().style)
        assertEquals(first.state, restored.state)
    }
    @Test fun emptyOperationsAndReset() {
        val service = StatisticsService()
        service.record(CaseConverter.Style.SNAKE, 0, 4, 100)
        assertEquals(0L, service.state.snakeOperations)
        service.record(CaseConverter.Style.SNAKE, 1, 4, 100)
        service.clear()
        assertEquals(StatisticsService.Data(), service.state)
    }
    @Test fun settingsValidateAndCopyState() {
        val settings = PluginSettings()
        val input = PluginSettings.Data(historyLimit = -1)
        settings.loadState(input)
        input.historyLimit = 200
        assertEquals(10, settings.state.historyLimit)
        settings.state.collectStatistics = false
        assertTrue(settings.state.collectStatistics)
        settings.loadState(PluginSettings.Data(historyLimit = 10000))
        assertEquals(1000, settings.state.historyLimit)
    }
}

