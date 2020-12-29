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

import com.strobel.assembler.metadata.ArrayTypeLoader
import com.strobel.assembler.metadata.CompositeTypeLoader
import com.strobel.assembler.metadata.ITypeLoader
import com.strobel.decompiler.Decompiler
import com.strobel.decompiler.DecompilerSettings
import com.strobel.decompiler.PlainTextOutput
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
      isEnabled = true,
      expectedGreetMethod = """
        public static final String greet(@NotNull final String greeting, @NotNull final String name) {
            Intrinsics.checkNotNullParameter((Object)greeting, "greeting");
            Intrinsics.checkNotNullParameter((Object)name, "name");
            System.out.println((Object)("⇢ greet(greeting=" + greeting + ", name=" + name + ')'));
            final TimeMark markNow = TimeSource${'$'}Monotonic.INSTANCE.markNow();
            try {
                if (!Intrinsics.areEqual((Object)greeting, "Hello")) {
                    System.out.println((Object)new StringBuilder().append("⇠ greet [").append((Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc())).append("] = ").append("Early return").toString());
                    return "Early return";
                }
                Thread.sleep(15L);
                final String string = greeting + ", " + name + '!';
                System.out.println((Object)new StringBuilder().append("⇠ greet [").append((Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc())).append("] = ").append(string).toString());
                return string;
            }
            catch (Throwable t) {
                System.out.println((Object)new StringBuilder().append("⇠ greet [").append((Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc())).append("] = ").append((Object)t).toString());
                throw t;
            }
        }
    """.trimIndent(),

      expectedDoSomethingMethod = """
        public static final void doSomething() {
            System.out.println("⇢ doSomething()");
            final TimeMark markNow = TimeSource${'$'}Monotonic.INSTANCE.markNow();
            try {
                Thread.sleep(15L);
                System.out.println((Object)new StringBuilder().append("⇠ doSomething [").append((Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc())).append(']').toString());
            }
            catch (Throwable t) {
                System.out.println((Object)new StringBuilder().append("⇠ doSomething [").append((Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc())).append("] = ").append((Object)t).toString());
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
      isEnabled = false,
      expectedGreetMethod = """
        public static final String greet(@NotNull final String greeting, @NotNull final String name) {
            Intrinsics.checkNotNullParameter((Object)greeting, "greeting");
            Intrinsics.checkNotNullParameter((Object)name, "name");
            if (!Intrinsics.areEqual((Object)greeting, "Hello")) {
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
        assertEquals("Hello, World!", out[0])
        assertEquals("Hello, Kotlin IR!", out[1])
      }
    )
  }

  private fun testIrPlugin(
    isEnabled: Boolean,
    expectedGreetMethod: String,
    expectedDoSomethingMethod: String,
    outputVerifyFunc: (List<String>) -> Unit
  ) {
    val result = compile(sourceFile = main, DebugLogComponentRegistrar(isEnabled))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

    val mainJavaCode = result.javaCode("MainKt")
    assertEquals(expectedGreetMethod, methodGetter(mainJavaCode, "public static final String greet"))
    assertEquals(expectedDoSomethingMethod, methodGetter(mainJavaCode, "public static final void doSomething"))

    outputVerifyFunc(invokeMain(result, "MainKt").trim().split("""\r?\n+""".toRegex()))
  }

  private fun methodGetter(classText: String, methodPrefix: String): String {
    val lines = classText.split("\n")
    val firstLineIndex = lines.indexOfFirst { it.contains(methodPrefix) }
    val indentationCount = lines[firstLineIndex].takeWhile { it == ' ' }.length

    var curleyBraceCount = 1
    var currentIndex: Int = firstLineIndex + 1

    while (curleyBraceCount != 0 && currentIndex < lines.lastIndex) {
      if (lines[currentIndex].contains("{")) {
        curleyBraceCount++
      }
      if (lines[currentIndex].contains("}")) {
        curleyBraceCount--
      }

      currentIndex++
    }

    return lines
      .subList(firstLineIndex, currentIndex)
      .joinToString("\n") { it.substring(indentationCount) }
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
        // Add every class to the type loaders to ensure it's everything gets loaded correctly.
        generatedFiles.forEach {
          add(ArrayTypeLoader(it.readBytes()))
        }
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
