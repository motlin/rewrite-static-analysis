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
import static java.util.Collections.singletonList;

/**
 * Replaces explicit type declarations with {@code var} keyword when the initializer
 * is a constructor call with an exactly matching type.
 *
 * <p>This recipe is more conservative than {@code UseVarForObject} in
 * {@code rewrite-migrate-java}. It only transforms when the declared type exactly
 * matches the constructor type, avoiding cases where the declared type is an
 * interface or supertype.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class UseVarForConstructors extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `var` for constructor invocations";
    }

    @Override
    public String getDescription() {
        return "Replace explicit type declarations with `var` when the variable is initialized with a " +
               "constructor call of exactly the same type. Does not transform when declared type " +
               "differs from constructor type (e.g., interface vs implementation).";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofSeconds(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesJavaVersion<>(10), new UseVarForConstructorsVisitor());
    }

    private static final class UseVarForConstructorsVisitor extends JavaIsoVisitor<ExecutionContext> {

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
            if (initializer == null) {
                return false;
            }

            if (Literal.isLiteralValue(initializer, null)) {
                return false;
            }

            if (!(initializer instanceof NewClass)) {
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

            return typesMatch(vd, (NewClass) initializer);
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

        private boolean typesMatch(VariableDeclarations vd, NewClass newClass) {
            JavaType declaredType = vd.getType();
            JavaType constructorType = newClass.getType();

            if (declaredType == null || constructorType == null) {
                return false;
            }

            String declaredFqn = getFullyQualifiedName(declaredType);
            String constructorFqn = getFullyQualifiedName(constructorType);

            if (declaredFqn == null || constructorFqn == null) {
                return false;
            }

            return declaredFqn.equals(constructorFqn);
        }

        private static String getFullyQualifiedName(JavaType type) {
            if (!(type instanceof JavaType.FullyQualified)) {
                return null;
            }
            return ((JavaType.FullyQualified) type).getFullyQualifiedName();
        }

        private VariableDeclarations transformToVar(VariableDeclarations vd) {
            VariableDeclarations.NamedVariable variable = vd.getVariables().get(0);
            NewClass initializer = (NewClass) variable.getInitializer();

            Space prefix = vd.getTypeExpression() == null ? Space.EMPTY : vd.getTypeExpression().getPrefix();

            Identifier varIdentifier = new Identifier(
                    Tree.randomId(),
                    prefix,
                    Markers.EMPTY,
                    emptyList(),
                    "var",
                    initializer.getType(),
                    null
            );

            NewClass newInitializer = maybeTransferTypeArguments(vd, initializer);
            if (newInitializer != initializer) {
                VariableDeclarations.NamedVariable newVariable = variable.withInitializer(newInitializer);
                return vd.withTypeExpression(varIdentifier).withVariables(singletonList(newVariable));
            }

            return vd.withTypeExpression(varIdentifier);
        }

        private static NewClass maybeTransferTypeArguments(VariableDeclarations vd, NewClass initializer) {
            TypeTree typeExpression = vd.getTypeExpression();

            if (!(typeExpression instanceof ParameterizedType)) {
                return initializer;
            }
            ParameterizedType paramType = (ParameterizedType) typeExpression;

            List<Expression> declaredTypeParams = paramType.getTypeParameters();
            if (declaredTypeParams == null || declaredTypeParams.isEmpty()) {
                return initializer;
            }

            TypeTree constructorClazz = initializer.getClazz();
            if (!(constructorClazz instanceof ParameterizedType)) {
                return initializer;
            }
            ParameterizedType constructorParamType = (ParameterizedType) constructorClazz;

            List<Expression> constructorTypeParams = constructorParamType.getTypeParameters();
            if (constructorTypeParams == null || !isDiamondOperator(constructorTypeParams)) {
                return initializer;
            }

            ParameterizedType newClazz = constructorParamType.withTypeParameters(declaredTypeParams);
            return initializer.withClazz(newClazz);
        }

        private static boolean isDiamondOperator(List<Expression> typeParams) {
            return typeParams.isEmpty() || typeParams.stream().allMatch(Empty.class::isInstance);
        }
    }
}
