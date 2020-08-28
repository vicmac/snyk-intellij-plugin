package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliGroupedResult
import io.snyk.plugin.cli.Vulnerability
import io.snyk.plugin.head
import io.snyk.plugin.services.SnykTaskQueueService
import java.awt.BorderLayout
import java.awt.Insets
import java.util.Objects.nonNull
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


/**
 * Main panel for Snyk tool window.
 */
@Service
class SnykToolWindowPanel(val project: Project) : JPanel() {

    private val descriptionPanel = FullDescriptionPanel()

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val vulnerabilitiesTree = Tree(rootTreeNode)

    private val vulnerabilitiesSplitter = OnePixelSplitter(false, 0.4f, 0.1f, 0.9f)

    init {
        vulnerabilitiesTree.cellRenderer = VulnerabilityTreeCellRenderer()

        initializeUiComponents()

        displayNoVulnerabilitiesMessage()
    }

    fun cleanAll() {
        ApplicationManager.getApplication().invokeLater {
            removeAll()

            rootTreeNode.userObject = ""
            rootTreeNode.removeAllChildren()

            reloadTree()

            displayNoVulnerabilitiesMessage()

            revalidate()
        }
    }

    fun displayNoVulnerabilitiesMessage() {
        removeAll()

        val emptyStatePanel = JPanel()

        emptyStatePanel.add(JLabel("No vulnerabilities added. "))

        val runScanLinkLabel = LinkLabel.create("Run scan") {
            project.service<SnykTaskQueueService>().scan()
        }

        emptyStatePanel.add(runScanLinkLabel)

        add(CenterOneComponentPanel(emptyStatePanel), BorderLayout.CENTER)
    }

    fun displayScanningMessage() {
        removeAll()

        val scanningPanel = JPanel()

        scanningPanel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, -1)

        scanningPanel.add(JLabel("Scanning project for vulnerabilities..."),
            GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        scanningPanel.add(JLabel(""),
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false))

        val stopScanningLinkLabel = LinkLabel.create("Stop Scanning") {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        }

        scanningPanel.add(stopScanningLinkLabel,
            GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                0,
                false))

        add(CenterOneComponentPanel(scanningPanel), BorderLayout.CENTER)

        revalidate()
    }

    fun displayDownloadMessage() {
        removeAll()

        val downloadingPanel = JPanel()

        downloadingPanel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, -1)

        downloadingPanel.add(JLabel("Downloading Snyk CLI..."),
            GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        downloadingPanel.add(JLabel(""),
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false))

        val stopScanningLinkLabel = LinkLabel.create("Stop Downloading") {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        }

        downloadingPanel.add(stopScanningLinkLabel,
            GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                0,
                false))

        add(CenterOneComponentPanel(downloadingPanel), BorderLayout.CENTER)

        revalidate()
    }

    fun isEmpty(): Boolean = rootTreeNode.childCount == 0

    fun displayVulnerabilities(cliGroupedResult: CliGroupedResult) {
        ApplicationManager.getApplication().invokeLater {
            removeAll()

            rootTreeNode.userObject = "Found ${cliGroupedResult.uniqueCount} issues."

            add(vulnerabilitiesSplitter, BorderLayout.CENTER)

            vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
                ApplicationManager.getApplication().invokeLater {
                    descriptionPanel.removeAll()

                    val selectionPath = vulnerabilitiesTree.selectionPath

                    if (nonNull(selectionPath)) {
                        val node: DefaultMutableTreeNode = selectionPath!!.lastPathComponent as DefaultMutableTreeNode

                        if (node.userObject is Vulnerability) {
                            descriptionPanel.displayDescription(node.userObject as Vulnerability)
                        } else {
                            descriptionPanel.displaySelectVulnerabilityMessage()
                        }
                    }
                }
            }

            vulnerabilitiesSplitter.firstComponent = ScrollPaneFactory.createScrollPane(vulnerabilitiesTree)
            vulnerabilitiesSplitter.secondComponent = descriptionPanel

            val fileTreeNode = TargetFileTreeNode(cliGroupedResult.displayTargetFile)
            rootTreeNode.add(fileTreeNode)

            cliGroupedResult.vulnerabilitiesMap.keys.forEach { id ->
                fileTreeNode.add(VulnerabilityTreeNode(cliGroupedResult.vulnerabilitiesMap.getValue(id).head))
            }

            reloadTree()

            TreeUtil.expandAll(vulnerabilitiesTree)
        }
    }

    fun displayError(cliError: CliError) {
        removeAll()

        add(CliErrorPanel(cliError), BorderLayout.CENTER)

        revalidate()
    }

    fun displayCliCheckMessage() {
        removeAll()

        val checkingPanel = JPanel()

        checkingPanel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, -1)

        checkingPanel.add(JLabel("Checking Snyk CLI existence..."),
            GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        checkingPanel.add(JLabel(""),
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false))

        val stopCheckingLinkLabel = LinkLabel.create("Stop Checking") {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        }

        checkingPanel.add(stopCheckingLinkLabel,
            GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                0,
                false))

        add(CenterOneComponentPanel(checkingPanel), BorderLayout.CENTER)

        revalidate()
    }

    private fun initializeUiComponents() {
        layout = BorderLayout()

        TreeSpeedSearch(vulnerabilitiesTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true)
    }

    private fun reloadTree() {
        (vulnerabilitiesTree.model as DefaultTreeModel).reload()
    }
}