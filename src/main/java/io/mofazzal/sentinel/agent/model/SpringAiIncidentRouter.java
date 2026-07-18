package io.mofazzal.sentinel.agent.model;

import io.mofazzal.sentinel.agent.application.IncidentRouter;
import io.mofazzal.sentinel.agent.application.EvidenceSelectionPolicy;
import io.mofazzal.sentinel.agent.application.ModelCallBudget;
import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama")
public class SpringAiIncidentRouter implements IncidentRouter {

    private final ChatClient chat;
    private final ModelCallBudget budget;

    public SpringAiIncidentRouter(ChatClient.Builder builder, ModelCallBudget budget) {
        this.chat = builder.clone().defaultOptions(OllamaChatOptions.builder()
                .temperature(0.1)
                .format("json")).build();
        this.budget = budget;
    }

    @Override
    public Classification classify(TriageRequest request) {
        budget.acquire(request.incidentId(), "router");
        RouterResponse modelResult = chat.prompt()
                .system("""
                        You are Sentinel's incident router. Classify only as BAD_DEPLOY,
                        RESOURCE_EXHAUSTION, DEPENDENCY_OUTAGE, or UNKNOWN.
                        BAD_DEPLOY requires an explicit recent deploy or release correlation;
                        use signals DEPLOYMENTS, METRICS, and RUNBOOKS.
                        RESOURCE_EXHAUSTION means saturation or exhausted capacity without a
                        release correlation; use METRICS, LOGS, and RUNBOOKS.
                        DEPENDENCY_OUTAGE means an explicit downstream or external dependency
                        failure; use METRICS, LOGS, and RUNBOOKS.
                        Use UNKNOWN when the report does not establish an operational failure
                        or fit those definitions; use LOGS and RUNBOOKS. Do not infer a deploy.
                        Never propose or execute remediation in this role.
                        """)
                .user("Service: " + request.service() + "\nOccurred at: " + request.occurredAt()
                        + "\nSummary: " + request.summary())
                .call()
                .entity(RouterResponse.class);
        return new Classification(modelResult.type(),
                EvidenceSelectionPolicy.signalsFor(modelResult.type()), modelResult.rationale());
    }

    private record RouterResponse(IncidentType type, String rationale) {
    }
}
