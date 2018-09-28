package com.jetbrains.edu.learning.checker.gradle

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checker.CheckResult.FAILED_TO_CHECK
import com.jetbrains.edu.learning.checker.TaskChecker
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask

open class GradleEduTaskChecker(task: EduTask, project: Project) : TaskChecker<EduTask>(task, project) {
  override fun check(indicator: ProgressIndicator): CheckResult {
    val (taskName, params) = getGradleTask()
    if (task.testsText.isEmpty()) {
      return CheckResult(CheckStatus.Solved, "Task marked as completed")
    }

    return GradleCommandLine.create(project, taskName, *params.toTypedArray())
             ?.launchAndCheck()
             ?: FAILED_TO_CHECK
  }

  protected open fun getGradleTask() = GradleTask("${getGradleProjectName(task)}:$TEST_TASK_NAME")

  protected data class GradleTask(val taskName: String, val params: List<String> = emptyList())
}
