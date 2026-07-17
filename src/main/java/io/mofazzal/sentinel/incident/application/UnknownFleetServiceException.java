package io.mofazzal.sentinel.incident.application;

public class UnknownFleetServiceException extends RuntimeException {

    public UnknownFleetServiceException(String serviceName) {
        super("Unknown fleet service: " + serviceName);
    }
}
