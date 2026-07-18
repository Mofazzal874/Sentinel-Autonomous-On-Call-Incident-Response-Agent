package io.mofazzal.sentinel.agent.application;

public class AgentDispatchInProgressException extends RuntimeException {

    public AgentDispatchInProgressException(String message) {
        super(message);
    }
}
