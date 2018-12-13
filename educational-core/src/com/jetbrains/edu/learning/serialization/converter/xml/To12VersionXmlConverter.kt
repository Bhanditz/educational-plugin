package com.jetbrains.edu.learning.serialization.converter.xml

import com.jetbrains.edu.learning.serialization.SerializationUtils.Xml.*
import org.jdom.Element

class To12VersionXmlConverter : BaseXmlConverter() {

  override fun convertTaskElement(task: Element) {
    val taskFiles = getChildMap<String, Element>(task, TASK_FILES, true)
      .mapValuesTo(HashMap()) { (_, v) -> v.clone()}
    for ((path, additionalFile) in getChildMap<String, Element>(task, ADDITIONAL_FILES, true)) {
      val file = Element(TASK_FILE)
      file.addContent(additionalFile.cloneContent())
      taskFiles[path] = file
    }
    for ((path, text) in getChildMap<String, String>(task, TEST_FILES, true)) {
      val file = Element(TASK_FILE)
      addChildWithName(file, TEXT, text)
      addChildWithName(file, VISIBLE, false)
      taskFiles[path] = file
    }

    for (filesGroupName in listOf(TASK_FILES, ADDITIONAL_FILES, TEST_FILES)) {
      task.removeContent(getChildWithName(task, filesGroupName, true))
    }
    // TODO: use other name for this map
    addChildMap(task, TASK_FILES, taskFiles)
  }
}
