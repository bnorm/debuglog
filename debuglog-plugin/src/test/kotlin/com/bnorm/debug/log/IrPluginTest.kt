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
    if (greeting != "Hello") {
        return "Early return"
    }
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
    testIrPlugin(
      isPluginEnabled = true,
      expectedGreetMethod = """
        public static final String greet(@NotNull final String greeting, @NotNull final String name) {
            Intrinsics.checkNotNullParameter(greeting, "greeting");
            Intrinsics.checkNotNullParameter(name, "name");
            System.out.println((Object)("⇢ greet(greeting=" + greeting + ", name=" + name + ')'));
            final TimeMark markNow = TimeSource.Monotonic.INSTANCE.markNow();
            try {
                if (!Intrinsics.areEqual(greeting, "Hello")) {
                    System.out.println((Object)("⇠ greet [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + "Early return"));
                    return "Early return";
                }
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
    """.trimIndent(),

      expectedDoSomethingMethod = """
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
    """.trimIndent(),

      outputVerifyFunc = { out ->
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
    )
  }

  @Test
  fun `IR plugin disabled`() {
    testIrPlugin(
      isPluginEnabled = false,
      expectedGreetMethod = """
        public static final String greet(@NotNull final String greeting, @NotNull final String name) {
            Intrinsics.checkNotNullParameter(greeting, "greeting");
            Intrinsics.checkNotNullParameter(name, "name");
            if (!Intrinsics.areEqual(greeting, "Hello")) {
                return "Early return";
            }
            Thread.sleep(15L);
            return greeting + ", " + name + '!';
        }
      """.trimIndent(),

      expectedDoSomethingMethod = """
        public static final void doSomething() {
            Thread.sleep(15L);
        }
    """.trimIndent(),

      outputVerifyFunc = { out ->
        assertTrue(out.size == 2)
        assertTrue(out[0] == "Hello, World!")
        assertTrue(out[1] == "Hello, Kotlin IR!")
      }
    )
  }

  private fun testIrPlugin(
    isPluginEnabled: Boolean,
    expectedGreetMethod: String,
    expectedDoSomethingMethod: String,
    outputVerifyFunc: (List<String>) -> Unit
  ) {
    val result = compile(sourceFile = main, DebugLogComponentRegistrar(isPluginEnabled))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    result.javaCode("MainKt").also { javaCode ->
      assertFunction(javaCode, "public static final String greet", expectedGreetMethod)
      assertFunction(javaCode, "public static final void doSomething", expectedDoSomethingMethod)
    }

    outputVerifyFunc(invokeMain(result, "MainKt").trim().split("""\r?\n+""".toRegex()))
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
