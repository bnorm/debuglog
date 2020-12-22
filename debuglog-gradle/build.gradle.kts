import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("com.gradle.plugin-publish")
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
  val pluginProject = project(":debuglog-plugin")
  packageName(pluginProject.group.toString())
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${pluginProject.group}.${pluginProject.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

  val annotationProject = project(":debuglog-annotation")
  buildConfigField("String", "ANNOTATION_LIBRARY_GROUP", "\"${annotationProject.group}\"")
  buildConfigField("String", "ANNOTATION_LIBRARY_NAME", "\"${annotationProject.name}\"")
  buildConfigField("String", "ANNOTATION_LIBRARY_VERSION", "\"${annotationProject.version}\"")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
  plugins {
    create("debuglog") {
      id = "com.bnorm.debuglog"
      displayName = "Kotlin Debug Log compiler plugin"
      description = "Kotlin compiler plugin to add debug logging to functions"
      implementationClass = "com.bnorm.debug.log.DebugLogGradlePlugin"
    }
  }
}

pluginBundle {
  website = "https://github.com/bnorm/debuglog"
  vcsUrl = "https://github.com/bnorm/debuglog.git"
  tags = listOf("kotlin", "compiler-plugin")
}
