package com.example.executor.utility;

import lombok.Data;

@Data
public class Response {
    private Object data;
    private String status;
    private Object errorList;
    private String submissionId;
    private String userId;
    private Long problemId;
    private Double[] runtime;  // Runtime in milliseconds for each test case
    private Double[] memory;   // Memory usage in MB for each test case
}