package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PrevPlaceholderAction extends PlaceholderNavigationAction {
  public static final String ACTION_ID = "Educational.PrevPlaceholder";
  public static final String SHORTCUT = "ctrl shift pressed COMMA";

  public PrevPlaceholderAction() {
    super("Navigate to the Previous Answer Placeholder", "Navigate to the previous answer placeholder",
          AllIcons.Actions.Back);
  }


  @Nullable
  @Override
  protected AnswerPlaceholder getTargetPlaceholder(@NotNull final TaskFile taskFile, int offset) {
    final AnswerPlaceholder selectedAnswerPlaceholder = taskFile.getAnswerPlaceholder(offset);
    final List<AnswerPlaceholder> placeholders = taskFile.getActivePlaceholders();
    if (selectedAnswerPlaceholder == null) {
      for (int i = placeholders.size() - 1; i >= 0; i--) {
        final AnswerPlaceholder placeholder = placeholders.get(i);
        if (placeholder.getOffset() < offset) {
          return placeholder;
        }
      }
    }
    else {
      int prevIndex = selectedAnswerPlaceholder.getIndex() - 1;
      if (StudyUtils.indexIsValid(prevIndex, placeholders)) {
        return placeholders.get(prevIndex);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Nullable
  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }
}
