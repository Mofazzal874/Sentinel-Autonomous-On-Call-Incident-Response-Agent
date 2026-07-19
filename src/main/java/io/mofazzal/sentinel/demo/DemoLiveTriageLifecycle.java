package io.mofazzal.sentinel.demo;

import io.mofazzal.sentinel.alert.messaging.TriageCommand;
import io.mofazzal.sentinel.alert.messaging.TriageCommandLifecycleListener;
import io.mofazzal.sentinel.incident.repository.IncidentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@Profile("demo")
public class DemoLiveTriageLifecycle implements TriageCommandLifecycleListener {

    private final IncidentRepository incidents;
    private final DemoLiveSubmissionStore submissions;
    private final DemoSandboxRateLimiter limiter;
    private final Clock clock;

    public DemoLiveTriageLifecycle(IncidentRepository incidents, DemoLiveSubmissionStore submissions,
                                   DemoSandboxRateLimiter limiter, Clock clock) {
        this.incidents = incidents;
        this.submissions = submissions;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Override
    public void completed(TriageCommand command) {
        var incident = incidents.findByFingerprint(command.fingerprint());
        if (incident.isPresent() && submissions.complete(
                command.fingerprint(), incident.orElseThrow().getId(), clock.instant())) {
            limiter.release();
        }
    }

    @Override
    public void failed(TriageCommand command, String reason) {
        if (submissions.fail(command.fingerprint(), reason, clock.instant())) {
            limiter.release();
        }
    }
}
