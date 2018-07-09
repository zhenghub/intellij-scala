package org.jetbrains.plugins.scala.lang.formatting.settings

import java.awt._
import java.io.File

import com.intellij.application.options._
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.{JBCheckBox, JBTextField}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import javax.swing._
import javax.swing.event.ChangeEvent
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.rearranger.ScalaArrangementPanel

/**
 * User: Alefas
 * Date: 23.09.11
 */
class ScalaTabbedCodeStylePanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  extends TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {

  protected override def initTabs(settings: CodeStyleSettings) {
    super.initTabs(settings)
    addTab(new ScalaDocFormattingPanel(settings))
    addTab(new ImportsPanel(settings))
    addTab(new MultiLineStringCodeStylePanel(settings))
    addTab(new TypeAnnotationsPanel(settings))
    addTab(new ScalaArrangementPanel(settings))
    addTab(new OtherCodeStylePanel(settings))
    initOuterFormatterPanel()
  }

  override def isModified(settings: CodeStyleSettings): Boolean = {
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    super.isModified(settings) || scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER != useExternalFormatterCheckbox.isSelected ||
      scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH != externalFormatterSettingsPath.getText ||
      scalaCodeStyleSettings.REFORMAT_ON_COMPILE != reformatOnCompile.isSelected ||
      scalaCodeStyleSettings.AUTO_DETECT_SCALAFMT != autoDetectScalaFmt.isSelected ||
      scalaCodeStyleSettings.SUGGEST_AUTO_DETECT_SCALAFMT != suggestScalaFmtAutoDetection.isSelected ||
      shortenedPanel.exposeIsModified(settings)
  }

  override def apply(settings: CodeStyleSettings): Unit = {
    super.apply(settings)
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER = useExternalFormatterCheckbox.isSelected
    scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH = externalFormatterSettingsPath.getText
    scalaCodeStyleSettings.REFORMAT_ON_COMPILE = reformatOnCompile.isSelected
    scalaCodeStyleSettings.AUTO_DETECT_SCALAFMT = autoDetectScalaFmt.isSelected
    scalaCodeStyleSettings.SUGGEST_AUTO_DETECT_SCALAFMT != suggestScalaFmtAutoDetection.isSelected
    if (scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER) shortenedPanel.exposeApply(settings)
  }

  override def resetImpl(settings: CodeStyleSettings): Unit = {
    super.resetImpl(settings)
    val scalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    useExternalFormatterCheckbox.setSelected(scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER)
    externalFormatterSettingsPath.setEnabled(scalaCodeStyleSettings.USE_SCALAFMT_FORMATTER)
    externalFormatterSettingsPath.setText(scalaCodeStyleSettings.SCALAFMT_CONFIG_PATH)
    reformatOnCompile.setSelected(scalaCodeStyleSettings.REFORMAT_ON_COMPILE)
    autoDetectScalaFmt.setSelected(scalaCodeStyleSettings.AUTO_DETECT_SCALAFMT)
    suggestScalaFmtAutoDetection.setSelected(scalaCodeStyleSettings.SUGGEST_AUTO_DETECT_SCALAFMT)
    shortenedPanel.exposeResetImpl(settings)
  }

  private def initOuterFormatterPanel(): Unit = {
    outerPanel = new JPanel(new GridLayoutManager(7, 1, new Insets(0, 0, 0, 0), -1, -1))
    reformatOnCompile = new JCheckBox("Reformat on compile")
    outerPanel.add(reformatOnCompile, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
      null, 0, false))
    autoDetectScalaFmt = new JCheckBox("Automatically enable scalafmt for projects with configuration")
    outerPanel.add(autoDetectScalaFmt, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
      null, 0, false))
    suggestScalaFmtAutoDetection = new JBCheckBox("Suggest automatic scalafmt detection when a project is opened")
    outerPanel.add(suggestScalaFmtAutoDetection, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
      null, 0, false))
    useExternalFormatterCheckbox = new JCheckBox("Use scalafmt")
    outerPanel.add(useExternalFormatterCheckbox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
      null, 0, false))
    externalFormatterPanel = new JPanel(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1))
    externalFormatterPanel.add(new JLabel("Scalafmt config file path:"),
      new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
      null, 0, false))
    val myTextField = new JBTextField
    myTextField.getEmptyText.setText(s"Default: .${File.separatorChar}scalafmt.conf")
    externalFormatterSettingsPath = new TextFieldWithBrowseButton(myTextField)
    externalFormatterSettingsPath.addBrowseFolderListener(customSettingsTitle, customSettingsTitle, null,
      FileChooserDescriptorFactory.createSingleFileDescriptor("conf"))
    externalFormatterPanel.add(externalFormatterSettingsPath,
      new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    outerPanel.add(externalFormatterPanel,
      new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false))
    outerPanel.add(innerPanel,
      new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    outerPanel.add(shortenedPanel.getPanel,
      new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
        null, 0, false))
    useExternalFormatterCheckbox.addChangeListener((_: ChangeEvent) => {
      //USE_SCALAFMT_FORMATTER setting is immediately set to allow proper formatting for core formatter examples
      settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).USE_SCALAFMT_FORMATTER = useExternalFormatterCheckbox.isSelected
      toggleSettingsVisibility(useExternalFormatterCheckbox.isSelected)
      externalFormatterSettingsPath.setEnabled(useExternalFormatterCheckbox.isSelected)
    })
  }

  private def toggleSettingsVisibility(useExternalFormatter: Boolean): Unit = {
    innerPanel.setVisible(!useExternalFormatter)
    shortenedPanel.getPanel.setVisible(useExternalFormatter)
    externalFormatterPanel.setVisible(useExternalFormatter)
    val tempSettings = settings.clone()
    if (useExternalFormatter) {
      apply(tempSettings)
      shortenedPanel.exposeResetImpl(tempSettings)
    } else {
      shortenedPanel.exposeApply(tempSettings)
      resetImpl(tempSettings)
    }
  }

  private var autoDetectScalaFmt: JCheckBox = _
  private var suggestScalaFmtAutoDetection: JCheckBox = _
  private var reformatOnCompile: JCheckBox = _
  private var useExternalFormatterCheckbox: JCheckBox = _
  private var externalFormatterSettingsPath: TextFieldWithBrowseButton = _
  private var externalFormatterPanel: JPanel = _
  private var outerPanel: JPanel = _
  private def innerPanel = super.getPanel
  private val customSettingsTitle = "Select custom scalafmt configuration file"
  private var myModel: CodeStyleSchemesModel = _

  override def getPanel: JComponent = outerPanel

  private lazy val shortenedPanel = new TabbedLanguageCodeStylePanel(ScalaLanguage.INSTANCE, currentSettings, settings) {
    protected override def initTabs(settings: CodeStyleSettings): Unit = {
      addTab(new ImportsPanel(settings))
      addTab(new MultiLineStringCodeStylePanel(settings))
      addTab(new TypeAnnotationsPanel(settings))
      addTab(new ScalaArrangementPanel(settings))
      val otherCodeStylePanel: OtherCodeStylePanel = new OtherCodeStylePanel(settings)
      addTab(otherCodeStylePanel)
      otherCodeStylePanel.toggleExternalFormatter(true)
    }

    override def isModified(settings: CodeStyleSettings): Boolean = {
      super.isModified(settings)
    }

    override def apply(settings: CodeStyleSettings): Unit = {
      super.apply(settings)
    }

    override def resetImpl(settings: CodeStyleSettings): Unit = {
      super.resetImpl(settings)
    }

    def exposeIsModified(settings: CodeStyleSettings) = super.isModified(settings)
    def exposeApply(settings: CodeStyleSettings) = super.apply(settings)
    def exposeResetImpl(settings: CodeStyleSettings) = super.resetImpl(settings)
  }
}