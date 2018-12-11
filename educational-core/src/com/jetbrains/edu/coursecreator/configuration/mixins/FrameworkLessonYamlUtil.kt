package com.jetbrains.edu.coursecreator.configuration.mixins

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonAppend
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedClass
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition
import com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter
import com.fasterxml.jackson.databind.util.Annotations
import com.jetbrains.edu.learning.courseFormat.FrameworkLesson
import com.jetbrains.edu.learning.courseFormat.tasks.Task

private const val TYPE_PROPERTY_NAME = "type"
private const val TYPE_PROPERTY_VALUE = "framework"
private const val CONTENT = "content"

@JsonDeserialize(builder = FrameworkLessonBuilder::class)
@JsonAppend(props = [JsonAppend.Prop(LessonTypePropertyWriter::class, name = TYPE_PROPERTY_NAME, type = String::class)], prepend = true)
abstract class FrameworkLessonYamlUtil : LessonYamlMixin() {
  @JsonProperty(CONTENT)
  @JsonSerialize(contentConverter = StudyItemConverter::class)
  private lateinit var taskList: List<Task>
}

@JsonPOJOBuilder(withPrefix = "")
private class FrameworkLessonBuilder(@JsonProperty(CONTENT) val content: List<String?>) {
  @Suppress("unused") //used for deserialization
  private fun build(): FrameworkLesson {
    val lesson = FrameworkLesson()
    val items = parseTaskList(content)
    lesson.updateTaskList(items)
    return lesson
  }
}

private class LessonTypePropertyWriter : VirtualBeanPropertyWriter {

  @Suppress("unused")
  constructor()

  constructor(propDef: BeanPropertyDefinition, contextAnnotations: Annotations, declaredType: JavaType) : super(propDef,
                                                                                                                contextAnnotations,
                                                                                                                declaredType)

  override fun withConfig(config: MapperConfig<*>?,
                          declaringClass: AnnotatedClass,
                          propDef: BeanPropertyDefinition,
                          type: JavaType): VirtualBeanPropertyWriter {
    return LessonTypePropertyWriter(propDef, declaringClass.annotations, type)
  }

  override fun value(bean: Any, gen: JsonGenerator, prov: SerializerProvider): Any {
    return TYPE_PROPERTY_VALUE
  }
}
