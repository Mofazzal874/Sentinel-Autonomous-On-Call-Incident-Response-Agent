package io.mofazzal.sentinel.agent.application;

public class ModelCallBudgetExceededException extends RuntimeException {
    public ModelCallBudgetExceededException(String message) {
        super(message);
    }
}
