package io.mofazzal.sentinel.alert.messaging;

public class AlertPublishException extends RuntimeException {

    public AlertPublishException(String message) {
        super(message);
    }

    public AlertPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
