/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.edu.learning.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.hash.HashSet;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.stepik.courseFormat.StepikCourse;
import com.jetbrains.edu.learning.stepik.courseFormat.ext.StepikCourseExt;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class EduUsagesCollector extends ProjectUsagesCollector {
  public static final String GROUP_ID = "statistics.educational";

  public static void projectTypeCreated(@NotNull Course course) {
    advanceKey("project.created." + courseTypeId(course));
  }

  public static void projectTypeOpened(@NotNull Course course) {
    advanceKey("project.opened." + courseTypeId(course));
  }

  public static void projectTypeOpened(@NotNull String projectTypeId) {
    advanceKey("project.opened." + projectTypeId);
  }

  public static void taskChecked() {
    advanceKey("checkTask");
  }

  public static void hintShown() {
    advanceKey("showHint");
  }

  public static void taskNavigation() {
    advanceKey("navigateToTask");
  }

  public static void courseUploaded() {
    advanceKey("uploadCourse");
  }

  public static void createdCourseArchive() {
    advanceKey("courseArchive");
  }

  public static void courseArchiveImported() {
    advanceKey("courseArchiveImported");
  }

  public static void inCourseLinkClicked() {
    advanceKey("inCourseLink");
  }

  public static void externalLinkClicked() {
    advanceKey("externalLink");
  }

  public static void stepikLinkClicked() {
    advanceKey("stepikLinkClicked");
  }

  public static void progressFromWidget() {
    advanceKey("progressFromWidget");
  }

  public static void progressOnGenerateCourse() {
    advanceKey("progressOnGenerateCourse");
  }

  public static void loginFromWidget() {
    advanceKey("loginFromWidget");
  }

  public static void logoutFromWidget() {
    advanceKey("logoutFromWidget");
  }

  public static void loginFromSettings() {
    advanceKey("loginFromSettings");
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    return collectUsages();
  }

  @NotNull
  static Set<UsageDescriptor> collectUsages() {
    HashSet<UsageDescriptor> descriptors = new HashSet<>();
    getDescriptors().forEachEntry((key, value) -> {
      descriptors.add(new UsageDescriptor(key, value));
      return true;
    });
    getDescriptors().clear();
    return descriptors;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return GROUP_ID;
  }

  private static void advanceKey(@NotNull String key) {
    TObjectIntHashMap<String> descriptors = getDescriptors();
    int oldValue = descriptors.get(key);
    descriptors.put(key, oldValue + 1);
  }

  private static TObjectIntHashMap<String> getDescriptors() {
    return ServiceManager.getService(EduStatistics.class).getUsageDescriptors();
  }

  @NotNull
  private static String courseTypeId(@NotNull Course course) {
    if (course.isStudy()) {
      return course instanceof StepikCourse && StepikCourseExt.isAdaptive((StepikCourse)course) ? EduNames.ADAPTIVE : EduNames.STUDY;
    } else {
      return CCUtils.COURSE_MODE;
    }
  }
}
