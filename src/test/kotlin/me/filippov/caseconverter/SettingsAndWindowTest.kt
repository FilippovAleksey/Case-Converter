package me.filippov.caseconverter

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox
import javax.swing.JProgressBar
import javax.swing.JSpinner
import javax.swing.JTable

/** Проверяем рабочую форму, события обновления окна и сериализацию состояния. */
class SettingsAndWindowTest : BasePlatformTestCase() {
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

    private fun descendants(component: Component): List<Component> =
        listOf(component) + if (component is Container)
            component.components.flatMap { descendants(it) } else emptyList()

    fun testSettingsApplyResetAndDispose() {
        val configurable = SettingsConfigurable()
        try {
            val components = descendants(configurable.createComponent())
            val checks = components.filterIsInstance<JCheckBox>()
            checks[0].isSelected = false
            checks[2].isSelected = false
            components.filterIsInstance<JSpinner>().single().value = 30
            assertTrue(configurable.isModified)
            // До Apply сервис не изменился.
            assertTrue(PluginSettings.getInstance().state.collectStatistics)
            configurable.reset()
            assertFalse(configurable.isModified)
            checks[2].isSelected = false
            configurable.apply()
            assertFalse(PluginSettings.getInstance().state.collectStatistics)
            assertFalse(configurable.isModified)
        } finally { configurable.disposeUIResources() }
    }

    fun testChartAndTableReactToEvents() {
        val panel = StatisticsPanel(project)
        try {
            val components = descendants(panel)
            val bars = components.filterIsInstance<JProgressBar>()
            val table = components.filterIsInstance<JTable>().single()
            assertEquals(0, table.rowCount)
            assertEquals(0, bars[0].value)
            StatisticsService.getInstance().record(CaseConverter.Style.SNAKE, 1, 8, 100)
            StatisticsService.getInstance().record(CaseConverter.Style.CAMEL, 1, 9, 100)
            StatisticsService.notifyChanged()
            assertEquals(50, bars[0].value)
            assertEquals(50, bars[1].value)
            assertEquals(2, table.rowCount)
            assertEquals("camelCase", table.getValueAt(0, 1))
            StatisticsService.getInstance().clear()
            StatisticsService.notifyChanged()
            assertEquals(0, bars[0].value)
            assertEquals(0, table.rowCount)
        } finally { Disposer.dispose(panel) }
    }

    fun testStateXmlRoundTrip() {
        val settings = PluginSettings.Data(false, false, false, 30)
        assertEquals(settings, XmlSerializer.deserialize(XmlSerializer.serialize(settings), PluginSettings.Data::class.java))
        val service = StatisticsService.getInstance()
        service.record(CaseConverter.Style.CAMEL, 1, 12, 100)
        val state = service.state
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), StatisticsService.Data::class.java)
        assertEquals(state, restored)
    }
}

