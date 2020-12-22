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

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException

class IrPluginTest {

  private val main = SourceFile.kotlin(
    "main.kt", """
import com.bnorm.debug.log.DebugLog

fun main() {
    println(greet())
    println(greet(name = "Kotlin IR"))
    doSomething()
}

@DebugLog
fun greet(greeting: String = "Hello", name: String = "World"): String {
    Thread.sleep(15)
    return "${'$'}greeting, ${'$'}name!"
}

@DebugLog
fun doSomething() {
    Thread.sleep(15)
}
"""
  )

  @Test
  fun `IR plugin enabled`() {
    val result = compile(sourceFile = main, DebugLogComponentRegistrar(true))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val out = invokeMain(result, "MainKt").trim().split("\n")
    assert(out.size == 8)
    assert(out[0] == "⇢ greet(greeting=Hello, name=World)")
    assert(out[1].matches("⇠ greet \\[\\d+(\\.\\d+)?ms] = Hello, World!".toRegex()))
    assert(out[2] == "Hello, World!")
    assert(out[3] == "⇢ greet(greeting=Hello, name=Kotlin IR)")
    assert(out[4].matches("⇠ greet \\[\\d+(\\.\\d+)?ms] = Hello, Kotlin IR!".toRegex()))
    assert(out[5] == "Hello, Kotlin IR!")
    assert(out[6] == "⇢ doSomething()")
    assert(out[7].matches("⇠ doSomething \\[\\d+(\\.\\d+)?ms]".toRegex()))
  }

  @Test
  fun `IR plugin disabled`() {
    val result = compile(sourceFile = main, DebugLogComponentRegistrar(false))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val out = invokeMain(result, "MainKt").trim().split("\n")
    assertTrue(out.size == 2)
    assertTrue(out[0] == "Hello, World!")
    assertTrue(out[1] == "Hello, Kotlin IR!")
  }
}

fun compile(
  sourceFiles: List<SourceFile>,
  plugin: ComponentRegistrar,
): KotlinCompilation.Result {
  return KotlinCompilation().apply {
    sources = sourceFiles
    useIR = true
    compilerPlugins = listOf(plugin)
    inheritClassPath = true
    verbose = false
  }.compile()
}

fun compile(
  sourceFile: SourceFile,
  plugin: ComponentRegistrar,
): KotlinCompilation.Result {
  return compile(listOf(sourceFile), plugin)
}

fun invokeMain(result: KotlinCompilation.Result, className: String): String {
  val oldOut = System.out
  try {
    val buffer = ByteArrayOutputStream()
    System.setOut(PrintStream(buffer))

    try {
      val kClazz = result.classLoader.loadClass(className)
      val main = kClazz.declaredMethods.single { it.name == "main" && it.parameterCount == 0 }
      main.invoke(null)
    } catch (e: InvocationTargetException) {
      throw e.targetException
    }

    return buffer.toString()
  } finally {
    System.setOut(oldOut)
  }
}
