package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects mutable static state that would contaminate compile-once/run-many profiling.
 */
public final class ComplexityProfileMutableStaticScanner {

    private ComplexityProfileMutableStaticScanner() {
    }

    public static List<String> findViolations(Class<?> solutionClass) {
        List<String> violations = new ArrayList<>();
        for (Field field : solutionClass.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                continue;
            }
            if (Modifier.isFinal(modifiers) && isBenignCompileTimeConstant(field.getType())) {
                continue;
            }
            violations.add(field.getName());
        }
        return violations;
    }

    public static boolean hasUnsupportedMutableStaticState(Class<?> solutionClass) {
        return !findViolations(solutionClass).isEmpty();
    }

    private static boolean isBenignCompileTimeConstant(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Integer.class
                || type == Long.class
                || type == Double.class
                || type == Boolean.class;
    }
}
