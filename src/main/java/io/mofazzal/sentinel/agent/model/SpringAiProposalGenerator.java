package io.mofazzal.sentinel.agent.model;

import io.mofazzal.sentinel.agent.application.ModelCallBudget;
import io.mofazzal.sentinel.agent.application.ProposalGenerator;
import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama")
public class SpringAiProposalGenerator implements ProposalGenerator {

    private final ChatClient chat;
    private final ModelCallBudget budget;

    public SpringAiProposalGenerator(ChatClient.Builder builder, ModelCallBudget budget) {
        this.chat = builder.clone().defaultOptions(OllamaChatOptions.builder()
                .temperature(0.1)
                .format("json")).build();
        this.budget = budget;
    }

    @Override
    public RemediationProposal propose(TriageRequest request, Classification classification,
                                       EvidenceBundle evidence, String previousFeedback) {
        budget.acquire(request.incidentId(), "proposal-generator");
        return chat.prompt()
                .system("""
                        Draft one remediation proposal using only the supplied evidence. The
                        runbookTitle must exactly match a retrieved runbook. Describe risk but
                        never claim the action is approved or execute it. Use at most ten steps.
                        """)
                .user("Incident: " + request + "\nClassification: " + classification
                        + "\nEvidence: " + evidence + "\nPrevious critique: " + previousFeedback)
                .call()
                .entity(RemediationProposal.class);
    }
}
