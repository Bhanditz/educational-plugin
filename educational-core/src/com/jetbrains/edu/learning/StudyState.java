package com.jetbrains.edu.learning;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.editor.EduEditor;
import org.jetbrains.annotations.Nullable;

public class StudyState {
  private final EduEditor myEduEditor;
  private final Editor myEditor;
  private final TaskFile myTaskFile;
  private final VirtualFile myVirtualFile;
  private final Task myTask;
  private final VirtualFile myTaskDir;

  public StudyState(@Nullable final EduEditor eduEditor) {
    myEduEditor = eduEditor;
    myEditor = eduEditor != null ? eduEditor.getEditor() : null;
    myTaskFile = eduEditor != null ? eduEditor.getTaskFile() : null;
    myVirtualFile = myEditor != null ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
    myTaskDir = myVirtualFile != null ? StudyUtils.getTaskDir(myVirtualFile) : null;
    myTask = myTaskFile != null ? myTaskFile.getTask() : null;
  }

  public Editor getEditor() {
    return myEditor;
  }

  public TaskFile getTaskFile() {
    return myTaskFile;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public Task getTask() {
    return myTask;
  }

  public VirtualFile getTaskDir() {
    return myTaskDir;
  }

  public boolean isValid() {
    return myEduEditor != null && myEditor != null &&
           myTaskFile != null && myVirtualFile != null &&
           myTask != null && myTaskDir != null;
  }
}
