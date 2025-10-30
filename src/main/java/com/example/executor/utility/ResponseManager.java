package com.example.executor.utility;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ResponseManager {

    public Response success(Object data) {
        Response response = new Response();
        response.setData(data);
        response.setStatus("SUCCESS");
        response.setErrorList(Collections.emptyList());
        return response;
    }

    public Response error(Object error) {
        Response response = new Response();
        response.setData(null);
        response.setStatus("ERROR");
        response.setErrorList(error);
        return response;
    }
}