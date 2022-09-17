plugins {
  `kotlin-dsl`
  `kotlin-dsl-precompiled-script-plugins`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
  api("org.jetbrains.dokka:dokka-gradle-plugin:1.7.10")

  api("com.gradle.publish:plugin-publish-plugin:1.0.0")
  api("com.github.gmazzo:gradle-buildconfig-plugin:3.1.0")
  api("gradle.plugin.com.bnorm.power:kotlin-power-assert-gradle:0.12.0")
}
