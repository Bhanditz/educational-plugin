package com.jetbrains.edu.python.learning.newproject;

import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import com.jetbrains.edu.coursecreator.actions.CCCreateLesson;
import com.jetbrains.edu.coursecreator.actions.CCCreateTask;
import com.jetbrains.edu.learning.EduSettings;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.courseGeneration.StudyProjectGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.python.learning.PyEduPluginConfigurator;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.remote.PyProjectSynchronizer;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUpdater;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class PyDirectoryProjectGenerator extends PythonProjectGenerator<PyNewProjectSettings>
  implements EduCourseProjectGenerator<PyNewProjectSettings> {

  private static final Logger LOG = Logger.getInstance(PyDirectoryProjectGenerator.class);
  private static final String NO_PYTHON_INTERPRETER = "<html><u>Add</u> python interpreter.</html>";

  private final Course myCourse;
  private final StudyProjectGenerator myGenerator;

  private ValidationResult myValidationResult = new ValidationResult("selected course is not valid");

  public PyDirectoryProjectGenerator(@NotNull Course course) {
    myCourse = course;
    myGenerator = new StudyProjectGenerator();
    myGenerator.addSettingsStateListener(this::setValidationResult);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Educational";
  }

  @Override
  public void configureProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                               @NotNull PyNewProjectSettings settings,
                               @NotNull Module module,
                               @Nullable PyProjectSynchronizer synchronizer) {
    if (myCourse.isStudy()) {
      myGenerator.setSelectedCourse(myCourse);
      myGenerator.generateProject(project, baseDir);
      ApplicationManager.getApplication().runWriteAction(() -> createTestHelper(project, baseDir));
    } else {
      configureNewCourseProject(project, baseDir);
    }
  }

  private void configureNewCourseProject(@NotNull Project project, @NotNull VirtualFile baseDir) {
    StudyTaskManager.getInstance(project).setCourse(myCourse);

    ApplicationManager.getApplication().runWriteAction(() -> {
      createTestHelper(project, baseDir);
      VirtualFile lessonDir = new CCCreateLesson().createItem(project, baseDir, myCourse, false);
      if (lessonDir == null) {
        LOG.error("Failed to create lesson");
        return;
      }
      new CCCreateTask().createItem(project, lessonDir, myCourse, false);
    });
  }

  private static void createTestHelper(@NotNull Project project, @NotNull VirtualFile baseDir) {
    final String testHelper = EduNames.TEST_HELPER;
    if (baseDir.findChild(testHelper) != null) return;
    final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper");
    try {
      StudyGenerator.createChildFile(project.getBaseDir(), testHelper, template.getText());
    }
    catch (IOException exception) {
      LOG.error("Can't copy test_helper.py " + exception.getMessage());
    }
  }

  @NotNull
  @Override
  public ValidationResult validate() {
    final Project project = ProjectManager.getInstance().getDefaultProject();
    final List<Sdk> sdks = getAllSdks(project);

    ValidationResult validationResult;
    if (sdks.isEmpty()) {
      validationResult = new ValidationResult(NO_PYTHON_INTERPRETER);
    } else {
      validationResult = ValidationResult.OK;
    }

    return validationResult;
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String s) {
    ValidationResult validationResult = validate();
    if (!validationResult.isOk()) {
      myValidationResult = validationResult;
    }

    return myValidationResult;
  }

  @Override
  public boolean beforeProjectGenerated() {
    BooleanFunction<PythonProjectGenerator> function = beforeProjectGenerated(null);
    return function != null && function.fun(this);
  }

  @Override
  public void afterProjectGenerated(@NotNull Project project, @NotNull PyNewProjectSettings settings) {
    Sdk sdk = settings.getSdk();

    if (sdk != null && sdk.getSdkType() == FakePythonSdkType.INSTANCE) {
      createAndAddVirtualEnv(project, settings);
      sdk = settings.getSdk();
    }
    if (sdk instanceof PyDetectedSdk) {
      sdk = addDetectedSdk(sdk, project);
    }
    SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk);
  }

  private Sdk addDetectedSdk(@NotNull Sdk sdk, @NotNull Project project) {
    final String name = sdk.getName();
    VirtualFile sdkHome = WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(name));
    sdk = SdkConfigurationUtil.createAndAddSDK(sdkHome.getPath(), PythonSdkType.getInstance());
    if (sdk != null) {
      PythonSdkUpdater.updateOrShowError(sdk, null, project, null);
      addSdk(project, sdk);
    }

    return sdk;
  }

  public void setValidationResult(ValidationResult validationResult) {
    myValidationResult = validationResult;
  }

  @Nullable
  @Override
  public BooleanFunction<PythonProjectGenerator> beforeProjectGenerated(@Nullable Sdk sdk) {
    return generator -> {
      final List<Integer> enrolledCoursesIds = myGenerator.getEnrolledCoursesIds();
      final Course course = myGenerator.getSelectedCourse();
      if (course == null || !(course instanceof RemoteCourse)) return true;
      if (((RemoteCourse)course).getId() > 0 && !enrolledCoursesIds.contains(((RemoteCourse)course).getId())) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
          return StudyUtils.execCancelable(() -> EduStepicConnector.enrollToCourse(((RemoteCourse)course).getId(),
                                                                                   EduSettings.getInstance().getUser()));
        }, "Creating Course", true, ProjectManager.getInstance().getDefaultProject());
      }
      return true;
    };
  }

  public void createAndAddVirtualEnv(@NotNull Project project, @NotNull PyNewProjectSettings settings) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final String baseSdk = getBaseSdk(course);

    if (baseSdk != null) {
      final PyPackageManager packageManager = PyPackageManager.getInstance(new PyDetectedSdk(baseSdk));
      try {
        final String path = packageManager.createVirtualEnv(project.getBasePath() + "/.idea/VirtualEnvironment", false);
        AbstractCreateVirtualEnvDialog.setupVirtualEnvSdk(path, true, (createdSdk, associateWithProject) -> {
          settings.setSdk(createdSdk);
          addSdk(project, createdSdk);
          if (associateWithProject) {
            SdkAdditionalData additionalData = createdSdk.getSdkAdditionalData();
            if (additionalData == null) {
              additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(createdSdk.getHomePath()));
              ((ProjectJdkImpl)createdSdk).setSdkAdditionalData(additionalData);
            }
            ((PythonSdkAdditionalData)additionalData).associateWithNewProject();
          }
        });
      }
      catch (ExecutionException e) {
        LOG.warn("Failed to create virtual env " + e.getMessage());
      }
    }
  }

  static String getBaseSdk(@NotNull final Course course) {
    LanguageLevel baseLevel = LanguageLevel.PYTHON30;
    final String version = course.getLanguageVersion();
    if (PyEduPluginConfigurator.PYTHON_2.equals(version)) {
      baseLevel = LanguageLevel.PYTHON27;
    }
    else if (PyEduPluginConfigurator.PYTHON_3.equals(version)) {
      baseLevel = LanguageLevel.PYTHON31;
    }
    else if (version != null) {
      baseLevel = LanguageLevel.fromPythonVersion(version);
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getApplicableFlavors(false).get(0);
    String baseSdk = null;
    final Collection<String> baseSdks = flavor.suggestHomePaths();
    for (String sdk : baseSdks) {
      final String versionString = flavor.getVersionString(sdk);
      final String prefix = flavor.getName() + " ";
      if (versionString != null && versionString.startsWith(prefix)) {
        final LanguageLevel level = LanguageLevel.fromPythonVersion(versionString.substring(prefix.length()));
        if (level.isAtLeast(baseLevel)) {
          baseSdk = sdk;
          break;
        }
      }
    }
    return baseSdk != null ? baseSdk : baseSdks.iterator().next();
  }

  protected void addSdk(@NotNull Project project, @NotNull Sdk sdk) {
    SdkConfigurationUtil.addSdk(sdk);
  }

  @NotNull
  protected List<Sdk> getAllSdks(@NotNull Project project) {
    return ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
  }
}
