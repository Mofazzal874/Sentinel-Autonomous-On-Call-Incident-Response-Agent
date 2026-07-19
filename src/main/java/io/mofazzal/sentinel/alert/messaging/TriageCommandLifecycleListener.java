package io.mofazzal.sentinel.alert.messaging;

public interface TriageCommandLifecycleListener {
    void completed(TriageCommand command);
    void failed(TriageCommand command, String reason);
}
