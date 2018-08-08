package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.EduConfigurator;
import com.jetbrains.edu.learning.EduConfiguratorManager;
import com.jetbrains.edu.learning.EduUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.ext.CourseExt;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.gradle.generation.EduGradleUtils;
import com.jetbrains.edu.learning.projectView.DirectoryNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCNode extends DirectoryNode {
  public CCNode(@NotNull Project project,
                PsiDirectory value,
                ViewSettings viewSettings,
                @NotNull Task task) {
    super(project, value, viewSettings, task);
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    final AbstractTreeNode node = super.modifyChildNode(childNode);
    if (node != null) return node;
    Object value = childNode.getValue();
    if (value instanceof PsiElement) {
      PsiFile psiFile = ((PsiElement) value).getContainingFile();
      VirtualFile virtualFile = psiFile.getVirtualFile();

      Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (course == null) {
        return null;
      }
      EduConfigurator configurator = EduConfiguratorManager.forLanguage(course.getLanguageById());
      if (configurator == null) {
        return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings);
      }
      if (EduUtils.isTaskDescriptionFile(virtualFile.getName())) {
        return null;
      }
      if (!EduUtils.isTestsFile(myProject, virtualFile)) {
        return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings);
      } else {
        if (EduGradleUtils.isConfiguredWithGradle(myProject) && CCUtils.isCourseCreator(myProject)) {
          return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings);
        }
      }
    }
    return null;
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(PsiDirectory value) {
    return new CCNode(myProject, value, myViewSettings, myTask);
  }

  @Override
  protected void updateImpl(PresentationData data) {
    Project project = getProject();
    if (project != null && CCUtils.isCourseCreator(project) && EduGradleUtils.isConfiguredWithGradle(project)) {
      PsiDirectory dir = getValue();
      VirtualFile directoryFile = dir.getVirtualFile();
      String name = directoryFile.getName();

      Course course = StudyTaskManager.getInstance(myProject).getCourse();
      // course is not null because of `CCUtils.isCourseCreator(project)` check above
      assert course != null;

      if (name.equals(CourseExt.getSourceDir(course)) || name.equals(CourseExt.getTestDir(course))) {
        data.setPresentableText(name);
        return;
      }
    }

    super.updateImpl(data);
  }
}
