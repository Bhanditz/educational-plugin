package com.jetbrains.edu.coursecreator.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.coursecreator.actions.NewStudyItemInfo
import com.jetbrains.edu.coursecreator.actions.NewStudyItemUiModel
import javax.swing.JComponent

abstract class CCCreateStudyItemDialogBase(
  project: Project,
  protected val model: NewStudyItemUiModel,
  protected val positionPanel: CCItemPositionPanel?
) : CCDialogWrapperBase(project) {

  private val nameField: JBTextField = JBTextField(model.suggestedName, 30)
  private val validator: InputValidatorEx = CCUtils.PathInputValidator(model.parentDir)

  init {
    title = "Create New ${StringUtil.toTitleCase(model.itemType.presentableName)}"
  }

  override fun postponeValidation(): Boolean = false

  override fun createCenterPanel(): JComponent {
    addTextValidator(nameField) { text ->
      when {
        text.isNullOrEmpty() -> "Empty name"
        !validator.checkInput(text) -> validator.getErrorText(text)
        else -> null
      }
    }
    return panel {
      row("Name:") { nameField() }
      createAdditionalFields(this)
      positionPanel?.attach(this)
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? = nameField

  open fun showAndGetResult(): NewStudyItemInfo? =
    if (showAndGet()) NewStudyItemInfo(nameField.text, model.baseIndex + (positionPanel?.indexDelta ?: 0)) else null

  protected open fun createAdditionalFields(builder: LayoutBuilder) {}
}

class CCCreateStudyItemDialog(
  project: Project,
  model: NewStudyItemUiModel,
  positionPanel: CCItemPositionPanel?
) : CCCreateStudyItemDialogBase(project, model, positionPanel) {
  init { init() }
}
