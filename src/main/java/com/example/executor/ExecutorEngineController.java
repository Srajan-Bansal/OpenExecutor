package com.example.executor;

import com.example.executor.model.ExecutorInput;
import com.example.executor.service.ExecutorService;
import com.example.executor.utility.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/executor/api")
public class ExecutorEngineController {
    @Autowired
    ExecutorService executorService;

    @PostMapping("/executeCode")
    public Response executeCode(@RequestBody ExecutorInput executorInput) throws IOException, InterruptedException {
        return executorService.executeCode(executorInput);
    }
}