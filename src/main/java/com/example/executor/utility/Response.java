package com.example.executor.utility;

import lombok.Data;

@Data
public class Response {
    Object data;
    Object status;
    Object errorList;
}