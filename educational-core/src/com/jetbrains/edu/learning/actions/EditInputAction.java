package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.util.PlatformIcons;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.EduUtils;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.UserTest;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.editor.EduSingleFileEditor;
import com.jetbrains.edu.learning.ui.TestContentPanel;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditInputAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(EditInputAction.class.getName());
  private JBEditorTabs tabbedPane;
  private Map<TabInfo, UserTest> myEditableTabs = new HashMap<>();

  public EditInputAction() {
    super("Watch Test Input", "Watch test input", EducationalCoreIcons.WatchInput);
  }

  public void showInput(final Project project) {
    final Editor selectedEditor = EduUtils.getSelectedEditor(project);
    if (selectedEditor != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      final VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      final StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
      final Course course = studyTaskManager.getCourse();
      if (course == null) return;
      assert openedFile != null;
      TaskFile taskFile = EduUtils.getTaskFile(project, openedFile);
      assert taskFile != null;
      final Task currentTask = taskFile.getTask();
      tabbedPane = new JBEditorTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), project);
      tabbedPane.addListener(new TabsListener.Adapter() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          if (newSelection.getIcon() != null) {
            int tabCount = tabbedPane.getTabCount();
            VirtualFile taskDir = EduUtils.getTaskDir(course, openedFile);
            assert taskDir != null;
            VirtualFile testsDir = taskDir.findChild(EduNames.USER_TESTS);
            assert testsDir != null;
            UserTest userTest = createUserTest(testsDir, currentTask, studyTaskManager);
            userTest.setEditable(true);
            TestContentPanel testContentPanel = new TestContentPanel(userTest);
            TabInfo testTab = addTestTab(tabbedPane.getTabCount(), testContentPanel, currentTask, true);
            myEditableTabs.put(testTab, userTest);
            tabbedPane.addTabSilently(testTab, tabCount - 1);
            tabbedPane.select(testTab, true);
          }
        }
      });

      List<UserTest> userTests = studyTaskManager.getUserTests(currentTask);
      int i = 1;
      for (UserTest userTest : userTests) {
        String inputFileText = EduUtils.getFileText(null, userTest.getInput(), false, "UTF-8");
        String outputFileText = EduUtils.getFileText(null, userTest.getOutput(), false, "UTF-8");
        TestContentPanel myContentPanel = new TestContentPanel(userTest);
        myContentPanel.addInputContent(inputFileText);
        myContentPanel.addOutputContent(outputFileText);
        TabInfo testTab = addTestTab(i, myContentPanel, currentTask, userTest.isEditable());
        tabbedPane.addTabSilently(testTab, i - 1);
        if (userTest.isEditable()) {
          myEditableTabs.put(testTab, userTest);
        }
        i++;
      }
      TabInfo plusTab = new TabInfo(new JPanel());
      plusTab.setIcon(PlatformIcons.ADD_ICON);
      tabbedPane.addTabSilently(plusTab, tabbedPane.getTabCount());
      final JBPopup hint =
        JBPopupFactory.getInstance().createComponentPopupBuilder(tabbedPane.getComponent(), tabbedPane.getComponent())
          .setResizable(true)
          .setMovable(true)
          .setRequestFocus(true)
          .createPopup();
      EduSingleFileEditor selectedEduEditor = EduUtils.getSelectedEduEditor(project);
      assert selectedEduEditor != null;
      hint.showInCenterOf(selectedEduEditor.getComponent());
      hint.addListener(new HintClosedListener(currentTask, studyTaskManager));
    }
  }


  private static void flushBuffer(@NotNull final StringBuilder buffer, @NotNull final File file) {
    PrintWriter printWriter = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      printWriter = new PrintWriter(new FileOutputStream(file));
      printWriter.print(buffer.toString());
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
    }
    finally {
      EduUtils.closeSilently(printWriter);
    }
    EduUtils.synchronize();
  }

  private static UserTest createUserTest(@NotNull final VirtualFile testsDir,
                                         @NotNull final Task currentTask,
                                         StudyTaskManager studyTaskManager) {
    UserTest userTest = new UserTest();
    List<UserTest> userTests = studyTaskManager.getUserTests(currentTask);
    int testNum = userTests.size() + 1;
    String inputName = EduNames.USER_TEST_INPUT + testNum;
    File inputFile = new File(testsDir.getPath(), inputName);
    String outputName = EduNames.USER_TEST_OUTPUT + testNum;
    File outputFile = new File(testsDir.getPath(), outputName);
    userTest.setInput(inputFile.getPath());
    userTest.setOutput(outputFile.getPath());
    studyTaskManager.addUserTest(currentTask, userTest);
    return userTest;
  }

  private TabInfo addTestTab(int nameIndex, final TestContentPanel contentPanel, @NotNull final Task currentTask, boolean toBeClosable) {
    TabInfo testTab = toBeClosable ? createClosableTab(contentPanel, currentTask) : new TabInfo(contentPanel);
    return testTab.setText(EduNames.TEST_TAB_NAME + String.valueOf(nameIndex));
  }

  private TabInfo createClosableTab(TestContentPanel contentPanel, Task currentTask) {
    TabInfo closableTab = new TabInfo(contentPanel);
    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTab(closableTab, currentTask));
    closableTab.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    return closableTab;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    showInput(e.getProject());
  }

  private static class HintClosedListener extends JBPopupAdapter {
    private final Task myTask;
    private final StudyTaskManager myStudyTaskManager;

    private HintClosedListener(@NotNull final Task task, StudyTaskManager studyTaskManager) {
      myTask = task;
      myStudyTaskManager = studyTaskManager;
    }

    @Override
    public void onClosed(LightweightWindowEvent event) {
      for (final UserTest userTest : myStudyTaskManager.getUserTests(myTask)) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (userTest.isEditable()) {
            File inputFile = new File(userTest.getInput());
            File outputFile = new File(userTest.getOutput());
            flushBuffer(userTest.getInputBuffer(), inputFile);
            flushBuffer(userTest.getOutputBuffer(), outputFile);
          }
        });
      }
    }
  }

  private class CloseTab extends AnAction implements DumbAware {

    private final TabInfo myTabInfo;
    private final Task myTask;

    public CloseTab(final TabInfo info, @NotNull final Task task) {
      myTabInfo = info;
      myTask = task;
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setIcon(AllIcons.Actions.Close);
      e.getPresentation().setHoveredIcon(AllIcons.Actions.CloseHovered);
      e.getPresentation().setVisible(UISettings.getInstance().getShowCloseButton());
      e.getPresentation().setText("Delete test");
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      tabbedPane.removeTab(myTabInfo);
      UserTest userTest = myEditableTabs.get(myTabInfo);
      File testInputFile = new File(userTest.getInput());
      File testOutputFile = new File(userTest.getOutput());
      if (testInputFile.delete() && testOutputFile.delete()) {
        EduUtils.synchronize();
      } else {
        LOG.error("failed to delete user tests");
      }
      final Project project = e.getProject();
      if (project != null) {
        StudyTaskManager.getInstance(project).removeUserTest(myTask, userTest);
      }
    }
  }
  @Override
  public void update(final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);

    final Project project = e.getProject();
    if (project != null) {
      EduSingleFileEditor eduEditor = EduUtils.getSelectedEduEditor(project);
      if (eduEditor != null) {
        final List<UserTest> userTests = StudyTaskManager.getInstance(project).getUserTests(eduEditor.getTaskFile().getTask());
        if (!userTests.isEmpty()) {
          presentation.setEnabledAndVisible(true);
        }
      }
    }
  }
}
