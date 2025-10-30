package com.example.executor.model;

import lombok.Data;

@Data
public class ExecutorInput {
    String language;
    String code;
    long problem_id;
    String problem_name;
    long user_id;
}
