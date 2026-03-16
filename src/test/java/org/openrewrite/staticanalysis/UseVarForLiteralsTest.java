/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class UseVarForLiteralsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseVarForLiterals())
          .allSources(src -> src.markers(javaVersion(17)));
    }

    @Test
    @DocumentExample
    void replaceLiteralPatterns() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      String s = "hello";
                      int i = 42;
                      long l = 42L;
                      double d = 3.14;
                      float f = 3.14f;
                      boolean b = true;
                      char c = 'a';
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      var s = "hello";
                      var i = 42;
                      var l = 42L;
                      var d = 3.14;
                      var f = 3.14f;
                      var b = true;
                      var c = 'a';
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedForFieldDeclarations() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private final String field = "hello";
                  static final int CONST = 42;
              }
              """
          )
        );
    }

    @Test
    void unchangedForWideningConversion() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      long l = 42;
                      double d = 42;
                      float f = 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedForBoxedTypes() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      Integer i = 42;
                      Long l = 42L;
                      Boolean b = true;
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedForAlreadyVar() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      var s = "hello";
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedForNullInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      String s = null;
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedForNonLiteralInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      String s = "hello".trim();
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedForMultipleVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      String a = "a", b = "b";
                  }
              }
              """
          )
        );
    }
}
