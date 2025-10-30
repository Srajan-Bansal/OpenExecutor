package com.example.executor.service;

import com.example.executor.manager.ExecutorManager;
import com.example.executor.model.ExecutorInput;
import com.example.executor.utility.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ExecutorServiceImpl implements ExecutorService {
    @Autowired
    ExecutorManager executorManager;

    @Override
    public Response executeCode(ExecutorInput executorInput) throws IOException, InterruptedException {
        return executorManager.executeCode(executorInput);
    }
}
