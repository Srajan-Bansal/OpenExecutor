package com.example.executor.constants;

/**
 * Constants used across the executor application
 */
public final class ExecutorConstants {

    private ExecutorConstants() {
        // Utility class
    }

    // Sandbox Configuration
    public static final String BOX_ID = "0";
    public static final String BOX_BASE_PATH = "/var/local/lib/isolate/";
    public static final String BOX_PATH = BOX_BASE_PATH + BOX_ID + "/box";

    // Execution Limits
    public static final int TIME_LIMIT = 5;
    public static final int WALL_TIME_LIMIT = 10;
    public static final int MEMORY_LIMIT = 524288; // 512MB in KB - required for JVM memory allocation
    public static final int MAX_PROCESSES = 20;

    // Language Identifiers
    public static final String LANG_JAVA = "java";
    public static final String LANG_JAVASCRIPT = "javascript";
    public static final String LANG_JS = "js";

    // File Names
    public static final String JAVA_MAIN_FILE = "Main.java";
    public static final String JS_MAIN_FILE = "main.js";
    public static final String INPUT_FILE = "input.txt";

    // Redis Keys
    public static final String REDIS_PROBLEM_PREFIX = "problem:";
    public static final String REDIS_INPUTS_SUFFIX = ":inputs";
    public static final String REDIS_OUTPUTS_SUFFIX = ":outputs";

    // Kafka Topics
    public static final String KAFKA_TOPIC_EXECUTOR = "code-executor";
    public static final String KAFKA_TOPIC_RESULTS = "code-results";
    public static final String KAFKA_CONSUMER_GROUP = "code-executor-group";

    // Response Status
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";

    // Directories
    public static final String DIR_TESTS = "tests";
    public static final String DIR_INPUTS = "inputs";
    public static final String DIR_OUTPUTS = "outputs";

    // Java Memory Settings (optimized for low-memory systems)
    // Disabled compressed class pointers to eliminate compressed class space requirement
    public static final String JAVA_MEM_MAX = "-Xmx128m";
    public static final String JAVA_MEM_MIN = "-Xms64m";
    public static final String JAVA_METASPACE = "-XX:MaxMetaspaceSize=64m";
    public static final String JAVA_METASPACE_MIN = "-XX:MetaspaceSize=32m";
    public static final String JAVA_CODE_CACHE = "-XX:ReservedCodeCacheSize=24m";
    public static final String JAVA_DISABLE_COMPRESSED_CLASS = "-XX:-UseCompressedClassPointers";
    public static final String JAVA_GC = "-XX:+UseSerialGC";
    public static final String JAVA_TIERED_COMPILATION = "-XX:TieredStopAtLevel=1";
}
