package com.example.executor.manager;

import com.example.executor.model.ExecutorInput;
import com.example.executor.utility.Response;
import com.example.executor.utility.ResponseManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ExecutorManager {
    @Autowired
    ResponseManager responseManager;
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Value("${basePath}")
    private String BASE_PATH;

    private static final String BOX_ID = "0"; // isolate sandbox id (0–999)
    private static final String BOX_PATH = "/var/local/lib/isolate/" + BOX_ID + "/box";

    private String JAVA_PATH = "/usr/bin/java";
    private String JAVAC_PATH = "/usr/bin/javac";
    private String NODE_PATH = "/usr/bin/node";

    public Response executeCode(ExecutorInput executorInput) throws IOException, InterruptedException {
        try {
            String problemKey = "problem:" + executorInput.getProblem_name();
            List<String> inputs = (List<String>) redisTemplate.opsForValue().get(problemKey + ":inputs");
            List<String> outputs = (List<String>) redisTemplate.opsForValue().get(problemKey + ":outputs");

            if (inputs == null || outputs == null) {
                return responseManager.error("❌ Testcases not found in Redis for: " + executorInput.getProblem_name());
            }

            runCommand("isolate", "--box-id=" + BOX_ID, "--init");
            Files.createDirectories(Paths.get(BOX_PATH));

            String fileName = generateFileName(executorInput.getLanguage());
            Path codePath = Paths.get(BOX_PATH, fileName);
            Files.writeString(codePath, executorInput.getCode());

            if (executorInput.getLanguage().equalsIgnoreCase("java")) {
                String compileOutput = runCommand(JAVAC_PATH, BOX_PATH + "/Main.java");
                if (!compileOutput.trim().isEmpty()) {
                    return responseManager.error("❌ Compilation error:\n" + compileOutput);
                }
            }

            List<String> results = new ArrayList<>();
            boolean allPassed = true;

            for (int i = 0; i < inputs.size(); i++) {
                String input = inputs.get(i);
                String expected = outputs.get(i).trim();

                Path inputFile = Paths.get(BOX_PATH, "input.txt");
                Files.writeString(inputFile, input);

                String actualOutput;
                if (executorInput.getLanguage().equalsIgnoreCase("java")) {
                    // ✅ Run Java with input redirection and memory constraints
                    actualOutput = runInIsolateWithInput(BOX_ID, input,
                            JAVA_PATH,
                            "-Xmx128m",           // Max heap size: 128MB
                            "-Xms32m",            // Initial heap size: 32MB
                            "-XX:ReservedCodeCacheSize=32m",  // Code cache: 32MB
                            "-XX:CompressedClassSpaceSize=32m", // Compressed class space: 32MB
                            "-XX:MaxMetaspaceSize=64m",  // Metaspace: 64MB
                            "-XX:+UseSerialGC",   // Use lightweight GC
                            "-Xss256k",           // Thread stack size: 256KB
                            "Main");
                } else {
                    actualOutput = runInIsolateWithInput(BOX_ID, input, NODE_PATH, "main.js");
                }

                actualOutput = actualOutput.trim();

                if (actualOutput.equals(expected)) {
                    results.add("✅ Testcase " + (i + 1) + " passed");
                } else {
                    results.add("❌ Testcase " + (i + 1) + " failed\nExpected: [" + expected + "]\nGot: [" + actualOutput + "]");
                    allPassed = false;
                }
            }
            runCommand("isolate", "--box-id=" + BOX_ID, "--cleanup");
            return allPassed ? responseManager.success("🎉 All testcases passed!") : responseManager.success(results);
        } catch (Exception e) {
            e.printStackTrace();
            runCommand("isolate", "--box-id=" + BOX_ID, "--cleanup");
            return responseManager.error("💥 Execution failed: " + e.getMessage());
        }
    }

    private String runInIsolateWithInput(String boxId, String input, String... innerCommand) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("isolate");
        command.add("--box-id=" + boxId);
        command.add("--time=5"); // 5 second time limit
        command.add("--wall-time=10"); // 10 second wall time limit
        command.add("--mem=524288"); // 512MB memory limit (Java needs more)
        command.add("--processes=50"); // Allow multiple processes for JVM threads
        command.add("--stdin=input.txt"); // Use input file

        // Mount system directories for Java/Node libraries (read-only)
        command.add("--dir=/usr/lib/jvm=/usr/lib/jvm:maybe");
        command.add("--dir=/usr/share=/usr/share:maybe");
        command.add("--dir=/usr/bin=/usr/bin:maybe");
        command.add("--dir=/lib=/lib:maybe");
        command.add("--dir=/lib64=/lib64:maybe");
        command.add("--dir=/etc=/etc:maybe"); // Java needs timezone, locale data
        command.add("--dir=/tmp=/tmp:rw"); // Java needs temp directory (read-write)

        command.add("--run");
        command.add("--");

        command.addAll(Arrays.asList(innerCommand));

        return executeProcess(command);
    }

    private String executeProcess(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            // Filter out isolate metadata lines (OK, Time limit exceeded, etc.)
            if (line.startsWith("OK (") || line.startsWith("Time limit exceeded") ||
                    line.startsWith("Caught fatal signal") || line.startsWith("Exited with error status")) {
                continue; // Skip isolate metadata
            }
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 && output.isEmpty()) {
            output.append("Process exited with code: ").append(exitCode);
        }

        return output.toString();
    }

    public String runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        return output.toString();
    }

    private String generateFileName(String language) {
        if (language.equalsIgnoreCase("java")) return "Main.java";
        else if (language.equalsIgnoreCase("javascript") || language.equalsIgnoreCase("js")) return "main.js";
        return null;
    }

    @PostConstruct
    public void loadTestcases() {
        // Detect Java and Node real paths (resolve all symlinks)
        try {
            // Find real Java path
            String javaWhich = runCommand("which", "java").trim();
            if (!javaWhich.isEmpty()) {
                String javaReal = runCommand("readlink", "-f", javaWhich).trim();
                if (!javaReal.isEmpty() && Files.exists(Paths.get(javaReal))) {
                    JAVA_PATH = javaReal;
                    System.out.println("✅ Detected Java at: " + JAVA_PATH);
                }
            }

            // Find real Javac path
            String javacWhich = runCommand("which", "javac").trim();
            if (!javacWhich.isEmpty()) {
                String javacReal = runCommand("readlink", "-f", javacWhich).trim();
                if (!javacReal.isEmpty() && Files.exists(Paths.get(javacReal))) {
                    JAVAC_PATH = javacReal;
                    System.out.println("✅ Detected Javac at: " + JAVAC_PATH);
                }
            }

            // Find real Node path
            String nodeWhich = runCommand("which", "node").trim();
            if (!nodeWhich.isEmpty()) {
                String nodeReal = runCommand("readlink", "-f", nodeWhich).trim();
                if (!nodeReal.isEmpty() && Files.exists(Paths.get(nodeReal))) {
                    NODE_PATH = nodeReal;
                    System.out.println("✅ Detected Node at: " + NODE_PATH);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not detect executable paths, using defaults");
            e.printStackTrace();
        }

        try {
            Path basePath = Paths.get(BASE_PATH).toAbsolutePath().normalize();
            System.out.println("Resolved testcases path: " + basePath);
            Files.list(basePath).filter(Files::isDirectory).forEach(problemDir -> {
                String problemName = problemDir.getFileName().toString();
                Path testsDir = problemDir.resolve("tests");
                if (!Files.exists(testsDir)) {
                    System.out.println("⚠️ No tests folder found for: " + problemName);
                    return;
                }
                System.out.println("Loading testcases for: " + problemName);
                try {
                    List<String> inputs = readAllFiles(testsDir.resolve("inputs"));
                    List<String> outputs = readAllFiles(testsDir.resolve("outputs"));

                    redisTemplate.opsForValue().set("problem:" + problemName + ":inputs", inputs);
                    redisTemplate.opsForValue().set("problem:" + problemName + ":outputs", outputs);
                    System.out.println("✅ Loaded " + problemName + " (" + inputs.size() + " cases) into Redis.");
                } catch (IOException e) {
                    System.err.println("❌ Error reading testcases for " + problemName + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> readAllFiles(Path dirPath) throws IOException {
        if (!Files.exists(dirPath)) return List.of();
        return Files.list(dirPath).filter(Files::isRegularFile).sorted().map(path -> {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }
}