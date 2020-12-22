import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.github.gmazzo.buildconfig")
  id("com.bnorm.power.kotlin-power-assert")
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")

  kapt("com.google.auto.service:auto-service:1.0-rc7")
  compileOnly("com.google.auto.service:auto-service-annotations:1.0-rc7")

  testImplementation(project(":debuglog-annotation"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.2.6")
}

buildConfig {
  packageName(group.toString())
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"$group.$name\"")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}

tasks.test {
  useJUnitPlatform()
}

tasks.compileTestKotlin {
  kotlinOptions {
    useIR = true
  }
}
