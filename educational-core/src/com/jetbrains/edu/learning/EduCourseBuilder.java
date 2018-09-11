package com.jetbrains.edu.learning;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.actions.CCCreateLesson;
import com.jetbrains.edu.coursecreator.actions.CCCreateTask;
import com.jetbrains.edu.coursecreator.actions.NewStudyItemInfo;
import com.jetbrains.edu.coursecreator.actions.NewStudyItemUiModel;
import com.jetbrains.edu.coursecreator.ui.CCItemPositionPanel;
import com.jetbrains.edu.coursecreator.ui.NewStudyItemUiUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.ext.TaskExt;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils;
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * The main interface provides courses creation for some language.
 *
 * @param <Settings> container type holds course project settings state
 */
public interface EduCourseBuilder<Settings> {

  Logger LOG = Logger.getInstance(EduCourseBuilder.class);

  /**
   * Shows UI for new study item creation
   *
   * @param model some parameters for UI extracted from context where creating action was called
   * @param positionPanel additional panel to allow user to choose where new item should be located
   *                      if creating action was called with sibling item context.
   *                      {@code null} if the corresponding action was called with parent item context.
   *
   * @return properties for study item creation
   */
  @Nullable
  default NewStudyItemInfo showNewStudyItemUi(@NotNull Project project,
                                              @NotNull NewStudyItemUiModel model,
                                              @Nullable CCItemPositionPanel positionPanel) {
    return NewStudyItemUiUtils.showNewStudyItemDialog(project, model, positionPanel);
  }

  /**
   * Creates content (including its directory or module) of new lesson in project
   *
   * @param project Parameter is used in Java and Kotlin plugins
   * @param lesson  Lesson to create content for. It's already properly initialized and added to course.
   * @return VirtualFile of created lesson
   */
  default VirtualFile createLessonContent(@NotNull Project project,
                                          @NotNull Lesson lesson,
                                          @NotNull VirtualFile parentDirectory) {
    final VirtualFile[] lessonDirectory = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        lessonDirectory[0] = VfsUtil.createDirectoryIfMissing(parentDirectory, lesson.getName());
      } catch (IOException e) {
        LOG.error("Failed to create lesson directory", e);
      }
    });
    return lessonDirectory[0];
  }

  /**
   * Creates content (including its directory or module) of new task in project
   *
   * @param task Task to create content for. It's already properly initialized and added to corresponding lesson.
   * @return VirtualFile of created task
   */
  @Nullable
  default VirtualFile createTaskContent(@NotNull final Project project,
                                        @NotNull final Task task,
                                        @NotNull final VirtualFile parentDirectory) {
    try {
      GeneratorUtils.createTask(task, parentDirectory);
    } catch (IOException e) {
      LOG.error("Failed to create task", e);
    }
    final VirtualFile taskDir = parentDirectory.findChild(task.getName());
    if (!OpenApiExtKt.isUnitTestMode()) {
      refreshProject(project);
    }
    return taskDir;
  }

  /**
   * Allows to update project modules and the whole project structure
   */
  default void refreshProject(@NotNull final Project project) {}

  @Nullable
  default Lesson createInitialLesson(@NotNull Project project, @NotNull Course course) {
    Lesson lesson = new CCCreateLesson().createAndInitItem(project, course, null, new NewStudyItemInfo(EduNames.LESSON + 1, 1));
    Task task = new CCCreateTask().createAndInitItem(project, course, lesson, new NewStudyItemInfo(EduNames.TASK + 1, 1));
    if (task != null) {
      lesson.addTask(task);
    }
    return lesson;
  }

  /**
   * Add initial content for a new task: task and tests files if the corresponding files don't exist.
   * Supposed to use in course creator mode
   *
   * @param task initializing task
   */
  default void initNewTask(@NotNull final Lesson lesson, @NotNull final Task task, @NotNull NewStudyItemInfo info) {
    if (task.getTaskFiles().isEmpty()) {
      String sourceDir = TaskExt.getSourceDir(task);
      TaskFile taskFile = new TaskFile();
      String taskTemplateName = getTaskTemplateName();
      if (taskTemplateName != null) {
        taskFile.setName(GeneratorUtils.joinPaths(sourceDir, taskTemplateName));
        taskFile.setText(StringUtil.notNullize(EduUtils.getTextFromInternalTemplate(taskTemplateName)));
      } else {
        GeneratorUtils.DefaultFileProperties taskFileProperties =
          GeneratorUtils.createDefaultFile(task.getLesson().getCourse(), "Task", "type task text here");
        taskFile.setName(GeneratorUtils.joinPaths(sourceDir, taskFileProperties.getName()));
        taskFile.setText(taskFileProperties.getText());
      }
      task.addTaskFile(taskFile);
    }

    if (task.getTestsText().isEmpty()) {
      String testDir = TaskExt.getTestDir(task);
      String testTemplateName = getTestTemplateName();
      if (testTemplateName != null) {
        task.getTestsText().put(GeneratorUtils.joinPaths(testDir, testTemplateName), EduUtils.getTextFromInternalTemplate(testTemplateName));
      }
    }
  }

  @Nullable
  default String getTaskTemplateName() {
    return null;
  }

  @Nullable
  default String getTestTemplateName() {
    return null;
  }

  /**
   * @return object responsible for language settings
   * @see LanguageSettings
   */
  @NotNull
  LanguageSettings<Settings> getLanguageSettings();

  @Nullable
  default CourseProjectGenerator<Settings> getCourseProjectGenerator(@NotNull Course course) {
    return null;
  }
}
