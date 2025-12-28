package com.example.executor.service;

import com.example.executor.constants.ExecutorConstants;
import com.example.executor.enums.RuntimeErrorPattern;
import com.example.executor.model.ExecutorInput;
import com.example.executor.utility.Response;
import com.example.executor.utility.ResponseManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class ExecutorService {

    private final ResponseManager responseManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TestCaseLoader testCaseLoader;
    private final ObjectMapper objectMapper;

    public ExecutorService(ResponseManager responseManager, RedisTemplate<String, Object> redisTemplate,
                           KafkaTemplate<String, String> kafkaTemplate, TestCaseLoader testCaseLoader,
                           ObjectMapper objectMapper) {
        this.responseManager = responseManager;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.testCaseLoader = testCaseLoader;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = ExecutorConstants.KAFKA_TOPIC_EXECUTOR, groupId = ExecutorConstants.KAFKA_CONSUMER_GROUP)
    public void executeCode(ExecutorInput executorInput) throws IOException, InterruptedException {
        String boxId = String.valueOf(Math.abs(executorInput.getSubmissionId().hashCode()) % ExecutorConstants.MAX_BOX_ID);
        log.info("Received execution request for problem: {}, language: {}, submissionId: {}, using BOX_ID: {}",
                executorInput.getProblemName(), executorInput.getLanguage(), executorInput.getSubmissionId(), boxId);
        Response response;
        try {
            response = runExecution(executorInput, boxId);
        } catch (Exception e) {
            log.error("Execution failed for problem: {}", executorInput.getProblemName(), e);
            response = responseManager.error("Execution failed: " + e.getMessage());
        }
        response.setSubmissionId(executorInput.getSubmissionId());
        response.setUserId(executorInput.getUserId());
        response.setProblemId(executorInput.getProblemId());

        try {
            String jsonResponse = objectMapper.writeValueAsString(response);
            log.info("Sending JSON to Kafka: {}", jsonResponse);
            kafkaTemplate.send(ExecutorConstants.KAFKA_TOPIC_RESULTS, jsonResponse);
            log.info("Sent execution result for submission: {}", executorInput.getSubmissionId());
        } catch (Exception e) {
            log.error("Failed to serialize response to JSON for submission: {}", executorInput.getSubmissionId(), e);
        }
    }

    private Response runExecution(ExecutorInput executorInput, String boxId) throws IOException, InterruptedException {
        try {
            String problemKey = ExecutorConstants.REDIS_PROBLEM_PREFIX + executorInput.getProblemName();
            List<String> inputs = (List<String>) redisTemplate.opsForValue().get(problemKey + ExecutorConstants.REDIS_INPUTS_SUFFIX);
            List<String> outputs = (List<String>) redisTemplate.opsForValue().get(problemKey + ExecutorConstants.REDIS_OUTPUTS_SUFFIX);

            if (inputs == null || outputs == null) {
                return responseManager.error("Test cases not found for " + executorInput.getProblemName());
            }

            String boxPath = ExecutorConstants.BOX_BASE_PATH + boxId + "/box";
            runCommand("isolate", "--box-id=" + boxId, "--init");
            Files.createDirectories(Paths.get(boxPath));

            String fileName = generateFileName(executorInput.getLanguage());
            Path codePath = Paths.get(boxPath, fileName);
            Files.writeString(codePath, executorInput.getCode());

            if (executorInput.getLanguage().equalsIgnoreCase(ExecutorConstants.LANG_JAVA)) {
                String compileOutput = runCommand(testCaseLoader.getJavacPath(), boxPath + "/" + ExecutorConstants.JAVA_MAIN_FILE);
                if (!compileOutput.trim().isEmpty()) {
                    return responseManager.error("Compilation error:\n" + compileOutput);
                }
            }

            List<String> results = new ArrayList<>();
            Path inputFile = Paths.get(boxPath, ExecutorConstants.INPUT_FILE);

            for (int i = 0; i < inputs.size(); i++) {
                String input = inputs.get(i);
                String expected = outputs.get(i).trim();

                Files.writeString(inputFile, input);

                String actualOutput;
                if (executorInput.getLanguage().equalsIgnoreCase(ExecutorConstants.LANG_JAVA)) {
                    actualOutput = runInIsolateWithInput(boxId, input, testCaseLoader.getJavaPath(),
                            ExecutorConstants.JAVA_MEM_MAX, ExecutorConstants.JAVA_MEM_MIN,
                            ExecutorConstants.JAVA_METASPACE, ExecutorConstants.JAVA_METASPACE_MIN,
                            ExecutorConstants.JAVA_CODE_CACHE, ExecutorConstants.JAVA_DISABLE_COMPRESSED_CLASS,
                            ExecutorConstants.JAVA_GC, ExecutorConstants.JAVA_TIERED_COMPILATION, "Main");
                } else {
                    actualOutput = runInIsolateWithInput(boxId, input, testCaseLoader.getNodePath(), ExecutorConstants.JS_MAIN_FILE);
                }

                actualOutput = actualOutput.trim();

                if (RuntimeErrorPattern.isRuntimeError(actualOutput)) {
                    results.add(String.format("Test case %d failed with runtime error\nExpected: [%s]\nGot: [%s]",
                            i + 1, expected, actualOutput));
                    log.warn("Stopping execution due to runtime error at test case {}", i + 1);
                    break;
                }

                // Normalize both outputs to handle spacing inconsistencies in arrays
                String normalizedActual = normalizeOutput(actualOutput);
                String normalizedExpected = normalizeOutput(expected);

                if (normalizedActual.equals(normalizedExpected)) {
                    results.add("Test case " + (i + 1) + " passed");
                } else {
                    results.add(String.format("Test case %d failed\nExpected: [%s]\nGot: [%s]",
                            i + 1, expected, actualOutput));
                }
            }

            runCommand("isolate", "--box-id=" + boxId, "--cleanup");
            boolean allPassed = results.stream().noneMatch(r -> r.contains("failed"));
            return allPassed
                    ? responseManager.success("All test cases passed")
                    : responseManager.success(results);
        } catch (Exception e) {
            runCommand("isolate", "--box-id=" + boxId, "--cleanup");
            return responseManager.error("Execution failed: " + e.getMessage());
        }
    }

    private String runInIsolateWithInput(String boxId, String input, String... innerCommand) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("isolate");
        command.add("--box-id=" + boxId);
        command.add("--time=" + ExecutorConstants.TIME_LIMIT);
        command.add("--wall-time=" + ExecutorConstants.WALL_TIME_LIMIT);
        command.add("--mem=" + ExecutorConstants.MEMORY_LIMIT);
        command.add("--processes=" + ExecutorConstants.MAX_PROCESSES);
        command.add("--stdin=" + ExecutorConstants.INPUT_FILE);

        command.add("--dir=/usr/lib/jvm=/usr/lib/jvm:maybe");
        command.add("--dir=/usr/share=/usr/share:maybe");
        command.add("--dir=/usr/bin=/usr/bin:maybe");
        command.add("--dir=/lib=/lib:maybe");
        command.add("--dir=/lib64=/lib64:maybe");
        command.add("--dir=/etc=/etc:maybe");
        command.add("--dir=/tmp=/tmp:rw");

        command.add("--run");
        command.add("--");
        command.addAll(Arrays.asList(innerCommand));

        return executeProcess(command);
    }

    private String executeProcess(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("OK (") || line.startsWith("Time limit exceeded") ||
                        line.startsWith("Caught fatal signal") || line.startsWith("Exited with error status")) {
                    continue;
                }
                output.append(line).append("\n");
            }
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
        if (language.equalsIgnoreCase(ExecutorConstants.LANG_JAVA)) {
            return ExecutorConstants.JAVA_MAIN_FILE;
        } else if (language.equalsIgnoreCase(ExecutorConstants.LANG_JAVASCRIPT) || language.equalsIgnoreCase(ExecutorConstants.LANG_JS)) {
            return ExecutorConstants.JS_MAIN_FILE;
        }
        throw new IllegalArgumentException("Unsupported language: " + language);
    }

    private String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }
        return output.replaceAll("\\s+", "");
    }
}