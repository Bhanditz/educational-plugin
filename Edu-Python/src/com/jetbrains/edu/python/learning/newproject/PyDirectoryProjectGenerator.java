package com.jetbrains.edu.python.learning.newproject;

import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import com.jetbrains.edu.coursecreator.actions.CCCreateLesson;
import com.jetbrains.edu.coursecreator.actions.CCCreateTask;
import com.jetbrains.edu.learning.EduSettings;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils;
import com.jetbrains.edu.learning.courseGeneration.ProjectGenerator;
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator;
import com.jetbrains.edu.learning.stepic.StepicConnector;
import com.jetbrains.edu.python.learning.PyPluginConfigurator;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.remote.PyProjectSynchronizer;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkExtKt;
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
  implements CourseProjectGenerator<PyNewProjectSettings> {

  private static final Logger LOG = Logger.getInstance(PyDirectoryProjectGenerator.class);
  private static final String NO_PYTHON_INTERPRETER = "<html><u>Add</u> python interpreter.</html>";

  private final Course myCourse;
  private final ProjectGenerator myGenerator;

  private ValidationResult myValidationResult = new ValidationResult("selected course is not valid");

  // Some python API has been changed while 2017.3 (first version of python plugin with new API is 2017.3.173.3415.6).
  // To prevent exceptions because of it we should check if it is new API or not.
  protected final boolean myHasOldPythonApi;

  public PyDirectoryProjectGenerator(@NotNull Course course) {
    myCourse = course;
    myGenerator = new ProjectGenerator();
    myGenerator.addSettingsStateListener(this::setValidationResult);
    myHasOldPythonApi = hasOldPythonApi();
  }

  private boolean hasOldPythonApi() {
    try {
      // `com.jetbrains.python.sdk.PySdkExtKt` is part of new python API
      // so we can use it to determine if it is new python API or not.
      // This way looks easier than check version because
      // there are different IDE with python support: PyCharm C/P/EDU and other IDEs with python plugin
      // and we have to use separate way to check API version for each case.
      Class.forName("com.jetbrains.python.sdk.PySdkExtKt");
      return false;
    } catch (ClassNotFoundException e) {
      LOG.warn("Current python API is old");
      return true;
    }
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
      GeneratorUtils.createChildFile(project.getBaseDir(), testHelper, template.getText());
    }
    catch (IOException exception) {
      LOG.error("Can't copy test_helper.py " + exception.getMessage());
    }
  }

  @NotNull
  @Override
  public ValidationResult validate() {
    final List<Sdk> sdks = getAllSdks();

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

    if (sdk != null && sdk.getSdkType() == PyFakeSdkType.INSTANCE) {
      createAndAddVirtualEnv(project, settings);
      sdk = settings.getSdk();
    }
    sdk = updateSdkIfNeeded(project, sdk);
    SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk);
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
          return StudyUtils.execCancelable(() -> StepicConnector.enrollToCourse(((RemoteCourse)course).getId(),
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
    final String baseSdkPath = getBaseSdk(course);
    if (baseSdkPath != null) {
      final PyDetectedSdk baseSdk = new PyDetectedSdk(baseSdkPath);
      final String virtualEnvPath = project.getBasePath() + "/.idea/VirtualEnvironment";
      final Sdk sdk = PySdkExtKt.createSdkByGenerateTask(new Task.WithResult<String, ExecutionException>(project,
              "Creating Virtual Environment",
              false) {
        @Override
        protected String compute(@NotNull ProgressIndicator indicator) throws ExecutionException {
          indicator.setIndeterminate(true);
          final PyPackageManager packageManager = PyPackageManager.getInstance(baseSdk);
          return packageManager.createVirtualEnv(virtualEnvPath, false);
        }
      }, getAllSdks(), baseSdk, project.getBasePath());
      if (sdk == null) {
        LOG.warn("Failed to create virtual env in " + virtualEnvPath);
        return;
      }
      settings.setSdk(sdk);
      SdkConfigurationUtil.addSdk(sdk);
      PySdkExtKt.associateWithProject(sdk, project, false);
    }
  }

  static String getBaseSdk(@NotNull final Course course) {
    LanguageLevel baseLevel = LanguageLevel.PYTHON36;
    final String version = course.getLanguageVersion();
    if (PyPluginConfigurator.PYTHON_2.equals(version)) {
      baseLevel = LanguageLevel.PYTHON27;
    }
    else if (PyPluginConfigurator.PYTHON_3.equals(version)) {
      baseLevel = LanguageLevel.PYTHON36;
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

  @Nullable
  protected Sdk updateSdkIfNeeded(@NotNull Project project, @Nullable Sdk sdk) {
    if (!(sdk instanceof PyDetectedSdk)) {
      return sdk;
    }
    String name = sdk.getName();
    VirtualFile sdkHome = WriteAction.compute(new ThrowableComputable<VirtualFile, RuntimeException>() {
      @Override
      public VirtualFile compute() throws RuntimeException {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(name);
        return null;
      }
    });
    Sdk newSdk = SdkConfigurationUtil.createAndAddSDK(sdkHome.getPath(), PythonSdkType.getInstance());
    if (newSdk != null) {
      PythonSdkUpdater.updateOrShowError(newSdk, null, project, null);
      SdkConfigurationUtil.addSdk(newSdk);
    }
    return newSdk;
  }

  @NotNull
  protected List<Sdk> getAllSdks() {
    return ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
  }
}
