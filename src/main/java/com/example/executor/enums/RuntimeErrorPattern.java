package com.example.executor.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Defines patterns for identifying runtime errors in code execution output.
 * This enum provides a structured and maintainable way to detect various types of runtime errors.
 */
public enum RuntimeErrorPattern {

    JVM_INITIALIZATION_ERROR(Arrays.asList(
        "Error occurred during initialization of VM",
        "Could not reserve enough space",
        "Could not create the Java Virtual Machine",
        "Could not allocate",
        "There is insufficient memory",
        "Failed to reserve memory",
        "Native memory allocation",
        "hs_err_pid"
    )),

    JAVA_EXCEPTION(Arrays.asList(
        "Exception in thread",
        "at java.",
        "at sun."
    )),

    SYSTEM_ERROR(Arrays.asList(
        "Segmentation fault",
        "core dumped",
        "fatal error"
    )),

    PROCESS_EXIT_ERROR(Arrays.asList(
        "Process exited with code:"
    ));

    private final List<String> patterns;

    RuntimeErrorPattern(List<String> patterns) {
        this.patterns = patterns;
    }

    /**
     * Checks if the output matches any pattern in this category.
     *
     * @param output the execution output to check
     * @return true if any pattern matches, false otherwise
     */
    public boolean matches(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }

        // Special case for JAVA_EXCEPTION
        if (this == JAVA_EXCEPTION) {
            return output.contains("Exception in thread") ||
                   output.contains("at java.") ||
                   output.contains("at sun.") ||
                   (output.contains("java.lang.") &&
                    (output.contains("Exception") || output.contains("Error")));
        }

        // Special case for PROCESS_EXIT_ERROR
        if (this == PROCESS_EXIT_ERROR) {
            return output.contains("Process exited with code:") &&
                   !output.contains("code: 0");
        }

        // Default: check if output contains any of the patterns
        return patterns.stream().anyMatch(output::contains);
    }

    /**
     * Checks if the output matches any runtime error pattern.
     *
     * @param output the execution output to check
     * @return true if the output indicates a runtime error, false otherwise
     */
    public static boolean isRuntimeError(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }

        return Arrays.stream(RuntimeErrorPattern.values())
                .anyMatch(pattern -> pattern.matches(output));
    }
}
