package com.example.executor.service;

import com.example.executor.model.ExecutorInput;
import com.example.executor.utility.Response;

import java.io.IOException;

public interface ExecutorService {

    Response executeCode(ExecutorInput executorInput) throws IOException, InterruptedException;
}
