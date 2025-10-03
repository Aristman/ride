package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import ru.marslab.ide.ride.settings.SettingsConfigurable

/**
 * Открыть настройки плагина Ride.
 */
class OpenSettingsAction : AnAction("Settings", "Open Ride settings", com.intellij.icons.AllIcons.General.Settings) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable::class.java)
    }
}
