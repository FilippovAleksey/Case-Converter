import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.2")
        testFramework(TestFrameworkType.Platform)
        // Java нужен для интеграционных тестов поиска ссылок, а не коду плагина.
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Plugin.Java)

        // Языковой модуль нужен в runIde и интеграционных тестах .py.
        // Это зависимость окружения разработки, а не обязательный depends в plugin.xml:
        // установленный Case Converter продолжает работать в IDE без Python.
        compatiblePlugin("PythonCore")

        // Только общая платформа: конвертер не зависит от языка кода.
    }
}

kotlin { jvmToolchain(25) }

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "262" }
    }
}
