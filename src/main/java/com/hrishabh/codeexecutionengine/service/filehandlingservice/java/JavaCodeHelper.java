package com.hrishabh.codeexecutionengine.service.filehandlingservice.java;

import java.util.Set;

public class JavaCodeHelper {

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
            "int", "Integer", "double", "Double", "boolean", "Boolean", "String"
    );

    public static boolean isPrimitiveOrWrapper(String type) {
        return PRIMITIVE_TYPES.contains(type);
    }
}