/*
 * Copyright (C) 2020 Brian Norman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bnorm.debug.log

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class DebugLogGradlePlugin : KotlinCompilerPluginSupportPlugin {
  override fun apply(target: Project): Unit = with(target) {
    extensions.create("debuglog", DebugLogGradleExtension::class.java)
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
    artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
    version = BuildConfig.KOTLIN_PLUGIN_VERSION
  )

  override fun getPluginArtifactForNative(): SubpluginArtifact = SubpluginArtifact(
    groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
    artifactId = BuildConfig.KOTLIN_PLUGIN_NAME + "-native",
    version = BuildConfig.KOTLIN_PLUGIN_VERSION
  )

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    kotlinCompilation.dependencies {
      implementation("${BuildConfig.ANNOTATION_LIBRARY_GROUP}:${BuildConfig.ANNOTATION_LIBRARY_NAME}:${BuildConfig.ANNOTATION_LIBRARY_VERSION}")
    }

    val project = kotlinCompilation.target.project
    val extension = project.extensions.getByType(DebugLogGradleExtension::class.java)

    return project.provider {
      listOf(
        SubpluginOption(key = "enabled", value = extension.enabled.toString()),
      )
    }
  }
}
