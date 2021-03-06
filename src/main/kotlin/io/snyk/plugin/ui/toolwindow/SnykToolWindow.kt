package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.snykcode.SnykCodeResults

/**
 * IntelliJ ToolWindow for Snyk plugin.
 */
class SnykToolWindow(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {

    private val actionToolbar: ActionToolbar

    init {
        val toolWindowPanel = project.service<SnykToolWindowPanel>()

        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("io.snyk.plugin.ActionBar") as ActionGroup
        actionToolbar = actionManager.createActionToolbar("Snyk Toolbar", actionGroup, false)
        initialiseToolbar()
        toolbar = actionToolbar.component

        setContent(toolWindowPanel)
    }

    private fun initialiseToolbar() {
        // update actions presentation immediately after running state changes (avoid default 500 ms delay)
        project.messageBus.connect(this)
            .subscribe(SnykScanListener.SNYK_SCAN_TOPIC, object : SnykScanListener {

                override fun scanningStarted() = updateActionsPresentation()

                override fun scanningCliFinished(cliResult: CliResult) = updateActionsPresentation()

                override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults) = updateActionsPresentation()

                override fun scanningCliError(cliError: CliError) = updateActionsPresentation()

                override fun scanningSnykCodeError(cliError: CliError) = updateActionsPresentation()
            })

        project.messageBus.connect(this)
            .subscribe(SnykTaskQueueListener.TASK_QUEUE_TOPIC, object : SnykTaskQueueListener {
                override fun stopped(wasCliRunning: Boolean, wasSnykCodeRunning: Boolean) = updateActionsPresentation()
            })
    }

    private fun updateActionsPresentation() =
        ApplicationManager.getApplication().invokeLater { actionToolbar.updateActionsImmediately() }

    override fun dispose() = Unit
}
