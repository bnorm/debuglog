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

import com.strobel.assembler.InputTypeLoader
import com.strobel.assembler.metadata.ArrayTypeLoader
import com.strobel.assembler.metadata.CompositeTypeLoader
import com.strobel.assembler.metadata.ITypeLoader
import com.strobel.decompiler.Decompiler
import com.strobel.decompiler.DecompilerSettings
import com.strobel.decompiler.PlainTextOutput
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.utils.indexOfFirst
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringWriter
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

  private val earlyReturn = SourceFile.kotlin(
    "earlyReturn.kt", """
import com.bnorm.debug.log.DebugLog

@DebugLog
fun earlyReturn(input: String): String {
    if (input == "EARLY_RETURN_1") {
        return "Early return - 1"
    }
    if (input == "EARLY_RETURN_2") {
        return "Early return - 2"
    }
    return "Normal return"
}
"""
  )

  private val earlyExit = SourceFile.kotlin(
    "earlyExit.kt", """
import com.bnorm.debug.log.DebugLog

@DebugLog
fun earlyExit(input: String) {
    if (input == "EARLY_RETURN_1") {
        return
    }
    if (input == "EARLY_RETURN_2") {
        return
    }
}
"""
  )

  @Test
  fun `IR plugin enabled`() {
    val result = compile(sourceFile = main, DebugLogComponentRegistrar(true))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val javaCode = result.javaCode("MainKt")

    assertFunction(javaCode, "public static final String greet",
      """
      public static final String greet(@NotNull final String greeting, @NotNull final String name) {
          Intrinsics.checkNotNullParameter(greeting, "greeting");
          Intrinsics.checkNotNullParameter(name, "name");
          System.out.println((Object)("⇢ greet(greeting=" + greeting + ", name=" + name + ')'));
          final TimeMark markNow = TimeSource.Monotonic.INSTANCE.markNow();
          try {
              Thread.sleep(15L);
              final String string = greeting + ", " + name + '!';
              System.out.println((Object)("⇠ greet [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + string));
              return string;
          }
          catch (Throwable t) {
              System.out.println((Object)("⇠ greet [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + t));
              throw t;
          }
      }
      """.trimIndent())

    assertFunction(javaCode, "public static final void doSomething",
      """
      public static final void doSomething() {
          System.out.println((Object)"⇢ doSomething()");
          final TimeMark markNow = TimeSource.Monotonic.INSTANCE.markNow();
          try {
              Thread.sleep(15L);
              System.out.println((Object)("⇠ doSomething [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + ']'));
          }
          catch (Throwable t) {
              System.out.println((Object)("⇠ doSomething [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + t));
              throw t;
          }
      }
      """.trimIndent())

    val out = invokeMain(result, "MainKt").trim().split("""\r?\n+""".toRegex())
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

    val javaCode = result.javaCode("MainKt")

    assertFunction(javaCode, "public static final String greet",
      """
      public static final String greet(@NotNull final String greeting, @NotNull final String name) {
          Intrinsics.checkNotNullParameter(greeting, "greeting");
          Intrinsics.checkNotNullParameter(name, "name");
          Thread.sleep(15L);
          return greeting + ", " + name + '!';
      }
      """.trimIndent())

    assertFunction(javaCode, "public static final void doSomething",
      """
      public static final void doSomething() {
          Thread.sleep(15L);
      }
      """.trimIndent())

    val out = invokeMain(result, "MainKt").trim().split("""\r?\n+""".toRegex())
    assertTrue(out.size == 2)
    assertTrue(out[0] == "Hello, World!")
    assertTrue(out[1] == "Hello, Kotlin IR!")
  }

  @Test
  fun `IR plugin enabled - with early return`() {
    val result = compile(sourceFile = earlyReturn, DebugLogComponentRegistrar(true))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    assertFunction(result.javaCode("EarlyReturnKt"), "public static final String earlyReturn",
      """
      public static final String earlyReturn(@NotNull final String input) {
          Intrinsics.checkNotNullParameter(input, "input");
          System.out.println((Object)("⇢ earlyReturn(input=" + input + ')'));
          final TimeMark markNow = TimeSource.Monotonic.INSTANCE.markNow();
          try {
              if (Intrinsics.areEqual(input, "EARLY_RETURN_1")) {
                  System.out.println((Object)("⇠ earlyReturn [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + "Early return - 1"));
                  return "Early return - 1";
              }
              if (Intrinsics.areEqual(input, "EARLY_RETURN_2")) {
                  System.out.println((Object)("⇠ earlyReturn [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + "Early return - 2"));
                  return "Early return - 2";
              }
              System.out.println((Object)("⇠ earlyReturn [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + "Normal return"));
              return "Normal return";
          }
          catch (Throwable t) {
              System.out.println((Object)("⇠ earlyReturn [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + t));
              throw t;
          }
      }
      """.trimIndent())
  }

  @Test
  fun `IR plugin enabled - with early exit`() {
    val result = compile(sourceFile = earlyExit, DebugLogComponentRegistrar(true))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    assertFunction(result.javaCode("EarlyExitKt"), "public static final void earlyExit",
      """
      public static final void earlyExit(@NotNull final String input) {
          Intrinsics.checkNotNullParameter(input, "input");
          System.out.println((Object)("⇢ earlyExit(input=" + input + ')'));
          final TimeMark markNow = TimeSource.Monotonic.INSTANCE.markNow();
          try {
              if (Intrinsics.areEqual(input, "EARLY_RETURN_1")) {
                  System.out.println((Object)("⇠ earlyExit [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + ']'));
                  return;
              }
              if (Intrinsics.areEqual(input, "EARLY_RETURN_2")) {
                  System.out.println((Object)("⇠ earlyExit [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + ']'));
                  return;
              }
              System.out.println((Object)("⇠ earlyExit [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + ']'));
          }
          catch (Throwable t) {
              System.out.println((Object)("⇠ earlyExit [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + t));
              throw t;
          }
      }
      """.trimIndent())
  }

  private fun assertFunction(javaCode: String, functionStatement: String, expectedFunction: String) {
    assertEquals(expectedFunction, fetchMethodByPrefix(javaCode, functionStatement))
  }

  private fun fetchMethodByPrefix(classText: String, methodSignaturePrefix: String): String {
    val classLines = classText.split("\n")
    val methodSignaturePredicate: (String) -> Boolean = { line -> line.contains(methodSignaturePrefix) }
    val methodFirstLineIndex = classLines.indexOfFirst(methodSignaturePredicate)

    check(methodFirstLineIndex != -1) {
      "Method with prefix '$methodSignaturePrefix' not found within class:\n$classText"
    }

    val multiplePrefixMatches = classLines
      .indexOfFirst(methodFirstLineIndex + 1, methodSignaturePredicate)
      .let { index -> index != -1 }

    check(!multiplePrefixMatches) {
      "Multiple methods with prefix '$methodSignaturePrefix' found within class:\n$classText"
    }

    val indentationSize = classLines[methodFirstLineIndex].takeWhile { it == ' ' }.length

    var curleyBraceCount = 1
    var currentLineIndex: Int = methodFirstLineIndex + 1

    while (curleyBraceCount != 0 && currentLineIndex < classLines.lastIndex) {
      if (classLines[currentLineIndex].contains("{")) {
        curleyBraceCount++
      }
      if (classLines[currentLineIndex].contains("}")) {
        curleyBraceCount--
      }
      currentLineIndex++
    }

    return classLines
      .subList(methodFirstLineIndex, currentLineIndex)
      .joinToString("\n") { it.substring(indentationSize) }
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
    System.setOut(PrintStream(buffer, false, "UTF-8"))

    try {
      val kClazz = result.classLoader.loadClass(className)
      val main = kClazz.declaredMethods.single { it.name == "main" && it.parameterCount == 0 }
      main.invoke(null)
    } catch (e: InvocationTargetException) {
      throw e.targetException
    }

    return buffer.toString("UTF-8")
  } finally {
    System.setOut(oldOut)
  }
}

fun KotlinCompilation.Result.javaCode(className: String): String {
  val decompilerSettings = DecompilerSettings.javaDefaults().apply {
    typeLoader = CompositeTypeLoader(*(mutableListOf<ITypeLoader>()
      .apply {
        // Ensure every class is available.
        generatedFiles.forEach {
          add(ArrayTypeLoader(it.readBytes()))
        }

        // Loads any standard classes already on the classpath.
        add(InputTypeLoader())
      }
      .toTypedArray()))

    isUnicodeOutputEnabled = true
  }

  return StringWriter().let { writer ->
    Decompiler.decompile(
      className,
      PlainTextOutput(writer).apply { isUnicodeOutputEnabled = true },
      decompilerSettings
    )
    writer.toString().trimEnd().trimIndent()
  }
}
