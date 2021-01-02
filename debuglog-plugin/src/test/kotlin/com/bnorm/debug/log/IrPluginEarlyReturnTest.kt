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

class IrPluginEarlyReturnTest {

  private val earlyReturn = SourceFile.kotlin(
    "EarlyReturn.kt", """
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
    "EarlyExit.kt", """
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
}
