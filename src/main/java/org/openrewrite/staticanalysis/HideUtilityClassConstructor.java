/*
 * Copyright 2024 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.util.Set;

import static java.util.Collections.singleton;

@Incubating(since = "7.0.0")
@EqualsAndHashCode(callSuper = false)
@Value
public class HideUtilityClassConstructor extends Recipe {

    @Option(displayName = "Constructor body",
            description = "Java statements to place inside newly generated private constructors, without enclosing braces. " +
                    "Defaults to an empty body. Existing constructor bodies are preserved. " +
                    "Use fully qualified names for types that are not already imported.",
            example = "throw new AssertionError(\"Suppress default constructor for noninstantiability\");",
            required = false)
    @Nullable
    String constructorBody;

    public HideUtilityClassConstructor() {
        this(null);
    }

    @JsonCreator
    public HideUtilityClassConstructor(@Nullable String constructorBody) {
        this.constructorBody = constructorBody;
    }

    String displayName = "Hide utility class constructor";

    String description = "Ensures utility classes (classes containing only static methods " +
            "or fields in their API) do not have a public constructor. " +
            "Instantiating a utility class is almost certainly a mistake, and a " +
            "private constructor makes that intent clear while preventing misuse.";

    Set<String> tags = singleton("RSPEC-S1118");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new HideUtilityClassConstructorVisitor<>(constructorBody);
    }
}
