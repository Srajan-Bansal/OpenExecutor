package com.example.executor.service;

import com.example.executor.constants.ExecutorConstants;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Data
public class TestCaseLoader {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${basePath}")
    private String basePath;

    private String javaPath = "/usr/bin/java";
    private String javacPath = "/usr/bin/javac";
    private String nodePath = "/usr/bin/node";

    public TestCaseLoader(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void initialize() {
        detectExecutablePaths();
        loadTestCasesIntoRedis();
    }

    private void detectExecutablePaths() {
        try {
            javaPath = detectExecutablePath("java", javaPath);
            javacPath = detectExecutablePath("javac", javacPath);
            nodePath = detectExecutablePath("node", nodePath);
        } catch (Exception e) {
            log.warn("Could not detect executable paths, using defaults", e);
        }
    }

    private String detectExecutablePath(String executableName, String defaultPath) throws IOException, InterruptedException {
        String whichOutput = runCommand("which", executableName).trim();
        if (!whichOutput.isEmpty()) {
            String realPath = runCommand("readlink", "-f", whichOutput).trim();
            if (!realPath.isEmpty() && Files.exists(Paths.get(realPath))) {
                log.info("Detected {} at: {}", executableName, realPath);
                return realPath;
            }
        }
        return defaultPath;
    }

    private void loadTestCasesIntoRedis() {
        try {
            Path resolvedPath = Paths.get(basePath).toAbsolutePath().normalize();
            log.info("Resolved test cases path: {}", resolvedPath);

            Files.list(resolvedPath)
                .filter(Files::isDirectory)
                .forEach(this::loadProblemTestCases);

        } catch (Exception e) {
            log.error("Failed to load test cases", e);
        }
    }

    private void loadProblemTestCases(Path problemDir) {
        String problemName = problemDir.getFileName().toString();
        Path testsDir = problemDir.resolve(ExecutorConstants.DIR_TESTS);

        if (!Files.exists(testsDir)) {
            log.debug("No tests folder found for: {}", problemName);
            return;
        }

        log.info("Loading test cases for: {}", problemName);

        try {
            List<String> inputs = readAllFiles(testsDir.resolve(ExecutorConstants.DIR_INPUTS));
            List<String> outputs = readAllFiles(testsDir.resolve(ExecutorConstants.DIR_OUTPUTS));

            String inputKey = ExecutorConstants.REDIS_PROBLEM_PREFIX + problemName + ExecutorConstants.REDIS_INPUTS_SUFFIX;
            String outputKey = ExecutorConstants.REDIS_PROBLEM_PREFIX + problemName + ExecutorConstants.REDIS_OUTPUTS_SUFFIX;

            redisTemplate.opsForValue().set(inputKey, inputs);
            redisTemplate.opsForValue().set(outputKey, outputs);

            log.info("Loaded {} ({} test cases) into Redis", problemName, inputs.size());
        } catch (IOException e) {
            log.error("Error reading test cases for {}", problemName, e);
        }
    }

    private List<String> readAllFiles(Path dirPath) throws IOException {
        if (!Files.exists(dirPath)) {
            return List.of();
        }

        return Files.list(dirPath)
            .filter(Files::isRegularFile).sorted().map(path -> {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
    }

    private String runCommand(String... command) throws IOException, InterruptedException {
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
}
