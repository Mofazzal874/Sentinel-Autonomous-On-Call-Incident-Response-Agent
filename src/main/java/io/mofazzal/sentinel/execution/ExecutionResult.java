package io.mofazzal.sentinel.execution;

record ExecutionResult(String details) {
    public ExecutionResult {
        if (details == null || details.isBlank()) {
            throw new IllegalArgumentException("details must not be blank");
        }
    }
}
