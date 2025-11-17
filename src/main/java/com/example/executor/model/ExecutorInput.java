package com.example.executor.model;

import lombok.Data;

@Data
public class ExecutorInput {
    private String language;
    private String code;
    private Long problemId;
    private String problemName;
    private String userId;
    private String submissionId;
}
