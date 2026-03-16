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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Replaces explicit type declarations with {@code var} keyword when the initializer
 * is a literal whose type exactly matches the declared type.
 *
 * <p>Handles String literals, primitive literals ({@code int}, {@code long},
 * {@code double}, {@code float}, {@code boolean}, {@code char}), but does not
 * transform when the declared type differs from the literal's natural type
 * (e.g., {@code long l = 42} stays unchanged because {@code var l = 42}
 * would infer {@code int}).
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class UseVarForLiterals extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `var` for literals";
    }

    @Override
    public String getDescription() {
        return "Replace explicit type declarations with `var` when the variable is initialized with a " +
               "literal value whose type exactly matches the declared type. Handles String and primitive " +
               "literals but does not transform when the declared type differs from the literal's " +
               "natural type (e.g., `long l = 42` is unchanged because `var` would infer `int`).";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofSeconds(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesJavaVersion<>(10), new UseVarForLiteralsVisitor());
    }

    private static final class UseVarForLiteralsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public VariableDeclarations visitVariableDeclarations(VariableDeclarations vd, ExecutionContext ctx) {
            VariableDeclarations result = super.visitVariableDeclarations(vd, ctx);

            if (!isApplicable(result)) {
                return result;
            }

            return transformToVar(result);
        }

        private boolean isApplicable(VariableDeclarations vd) {
            List<VariableDeclarations.NamedVariable> variables = vd.getVariables();
            if (variables.size() != 1) {
                return false;
            }

            VariableDeclarations.NamedVariable variable = variables.get(0);

            Expression initializer = variable.getInitializer();
            if (!(initializer instanceof Literal)) {
                return false;
            }

            Literal literal = (Literal) initializer;
            if (Literal.isLiteralValue(literal, null)) {
                return false;
            }

            TypeTree typeTree = vd.getTypeExpression();
            if (typeTree == null) {
                return false;
            }

            if (typeTree instanceof Identifier && "var".equals(((Identifier) typeTree).getSimpleName())) {
                return false;
            }

            if (isFieldDeclaration()) {
                return false;
            }

            return typesMatch(vd.getType(), literal.getType());
        }

        private boolean isFieldDeclaration() {
            Cursor parent = getCursor().getParentTreeCursor();
            if (parent.getParent() == null) {
                return false;
            }
            Cursor grandparent = parent.getParentTreeCursor();
            return parent.getValue() instanceof Block &&
                   (grandparent.getValue() instanceof ClassDeclaration ||
                    grandparent.getValue() instanceof NewClass);
        }

        private static boolean typesMatch(JavaType declaredType, JavaType literalType) {
            if (declaredType == null || literalType == null) {
                return false;
            }

            if (declaredType instanceof JavaType.Primitive && literalType instanceof JavaType.Primitive) {
                return declaredType == literalType;
            }

            // String literals have JavaType.Primitive.String but declared type is java.lang.String
            if (literalType == JavaType.Primitive.String &&
                declaredType instanceof JavaType.FullyQualified &&
                "java.lang.String".equals(((JavaType.FullyQualified) declaredType).getFullyQualifiedName())) {
                return true;
            }

            return false;
        }

        private VariableDeclarations transformToVar(VariableDeclarations vd) {
            Space prefix = vd.getTypeExpression() == null ? Space.EMPTY : vd.getTypeExpression().getPrefix();

            Identifier varIdentifier = new Identifier(
                    Tree.randomId(),
                    prefix,
                    Markers.EMPTY,
                    emptyList(),
                    "var",
                    vd.getVariables().get(0).getInitializer().getType(),
                    null
            );

            return vd.withTypeExpression(varIdentifier);
        }
    }
}
