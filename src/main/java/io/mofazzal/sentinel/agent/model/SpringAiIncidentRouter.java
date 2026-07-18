package io.mofazzal.sentinel.agent.model;

import io.mofazzal.sentinel.agent.application.IncidentRouter;
import io.mofazzal.sentinel.agent.application.ModelCallBudget;
import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ChatClient.Builder.class)
public class SpringAiIncidentRouter implements IncidentRouter {

    private final ChatClient chat;
    private final ModelCallBudget budget;

    public SpringAiIncidentRouter(ChatClient.Builder builder, ModelCallBudget budget) {
        this.chat = builder.clone().defaultOptions(ChatOptions.builder().temperature(0.1)).build();
        this.budget = budget;
    }

    @Override
    public Classification classify(TriageRequest request) {
        budget.acquire(request.incidentId(), "router");
        return chat.prompt()
                .system("""
                        You are Sentinel's incident router. Classify only as BAD_DEPLOY,
                        RESOURCE_EXHAUSTION, DEPENDENCY_OUTAGE, or UNKNOWN. Select only the
                        evidence signals needed from DEPLOYMENTS, METRICS, LOGS, and RUNBOOKS.
                        Never propose or execute remediation in this role.
                        """)
                .user("Service: " + request.service() + "\nOccurred at: " + request.occurredAt()
                        + "\nSummary: " + request.summary())
                .call()
                .entity(Classification.class);
    }
}
