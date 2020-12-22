plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm {
    compilations.all {
      kotlinOptions {
        kotlinOptions.jvmTarget = "1.8"
      }
    }
  }
  js(IR) {
    browser()
    nodejs()
  }

  val osName = System.getProperty("os.name")
  when {
    "Windows" in osName -> mingwX64("native")
    "Mac OS" in osName -> macosX64("native")
    else -> linuxX64("native")
  }

  sourceSets {
    val commonMain by getting {
    }
  }
}
