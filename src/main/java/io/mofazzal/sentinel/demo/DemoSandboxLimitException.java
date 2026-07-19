package io.mofazzal.sentinel.demo;

public class DemoSandboxLimitException extends RuntimeException {
    private final String code;

    public DemoSandboxLimitException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
