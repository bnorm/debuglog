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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
          catch (final Throwable t) {
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
          catch (final Throwable t) {
              System.out.println((Object)("⇠ doSomething [" + (Object)Duration.toString-impl(markNow.elapsedNow-UwyO8pc()) + "] = " + t));
              throw t;
          }
      }
      """.trimIndent())

    val out = invokeMain(result, "MainKt").trim().split("""\r?\n+""".toRegex())
    val iter = out.iterator()
    assert(out.size == 8)
    assert(iter.next() == "⇢ greet(greeting=Hello, name=World)")
    assert(iter.next() matches "⇠ greet \\[\\d+(\\.\\d+)?ms] = Hello, World!".toRegex())
    assert(iter.next() == "Hello, World!")
    assert(iter.next() == "⇢ greet(greeting=Hello, name=Kotlin IR)")
    assert(iter.next() matches "⇠ greet \\[\\d+(\\.\\d+)?ms] = Hello, Kotlin IR!".toRegex())
    assert(iter.next() == "Hello, Kotlin IR!")
    assert(iter.next() == "⇢ doSomething()")
    assert(iter.next() matches "⇠ doSomething \\[\\d+(\\.\\d+)?ms]".toRegex())
    assert(!iter.hasNext())
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
    val iter = out.iterator()
    assert(out.size == 2)
    assert(iter.next() == "Hello, World!")
    assert(iter.next() == "Hello, Kotlin IR!")
    assert(!iter.hasNext())
  }
}
