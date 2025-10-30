package com.example.executor.enums;

public enum LanguageType {
    JAVA("JAVA"), JAVASCRIPT("JAVASCRIPT");

    private final String value;

    LanguageType(String i) {
        this.value = i;
    }

    public String getValue() {
        return value;
    }
}
