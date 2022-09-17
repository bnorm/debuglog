plugins {
  kotlin("multiplatform") version "1.7.10"
  id("com.bnorm.debuglog") version "0.1.0"
}

repositories {
  mavenCentral()
}

kotlin {
  jvm()
  js(IR) {
    browser()
    nodejs()
  }

  val osName = System.getProperty("os.name")
  val osArch = System.getProperty("os.arch")
  when {
    "Windows" in osName -> mingwX64("native")
    "Mac OS" in osName -> when {
      "aarch64" in osArch -> macosArm64("native")
      else -> macosX64("native")
    }
    else -> linuxX64("native")
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        // TODO https://youtrack.jetbrains.com/issue/KT-43385
        compileOnly("com.bnorm.debug.log:debuglog-annotation:0.1.0")
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(kotlin("test-junit"))
      }
    }
    val jsTest by getting {
      dependencies {
        implementation(kotlin("test-js"))
      }
    }
    val nativeTest by getting {
      dependsOn(commonTest)
    }
  }
}

debuglog {
  enabled = true
}
