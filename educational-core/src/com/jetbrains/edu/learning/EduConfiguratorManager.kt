package com.jetbrains.edu.learning

import com.intellij.lang.Language
import com.intellij.openapi.extensions.Extensions

object EduConfiguratorManager {

  /**
   * Returns any enabled [EduConfigurator] for given language
   */
  @JvmStatic
  fun forLanguage(language: Language): EduConfigurator<*>? =
          allExtensions().find { extension -> extension.language == language.id }?.instance

  /**
   * Returns all extension points of [EduConfigurator] where instance of [EduConfigurator] is enabled
   */
  @JvmStatic
  fun allExtensions(): List<EduConfiguratorEP> =
    Extensions.getExtensions<EduConfiguratorEP>(EduConfigurator.EP_NAME, null)
                  .filter { it.instance.isEnabled }

}
