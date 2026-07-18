package io.mofazzal.sentinel.agent.model;

import io.mofazzal.sentinel.agent.application.ModelCallBudget;
import io.mofazzal.sentinel.agent.application.ProposalEvaluator;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.ProposalEvaluation;
import io.mofazzal.sentinel.agent.domain.RemediationProposal;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama")
public class SpringAiProposalEvaluator implements ProposalEvaluator {

    private final ChatClient chat;
    private final ModelCallBudget budget;

    public SpringAiProposalEvaluator(ChatClient.Builder builder, ModelCallBudget budget) {
        this.chat = builder.clone().defaultOptions(OllamaChatOptions.builder()
                .temperature(0.1)
                .format("json")).build();
        this.budget = budget;
    }

    @Override
    public ProposalEvaluation evaluate(TriageRequest request, EvidenceBundle evidence,
                                       RemediationProposal proposal) {
        budget.acquire(request.incidentId(), "proposal-evaluator");
        return chat.prompt()
                .system("""
                        Critique the proposal for evidence consistency, explicit verification,
                        bounded steps, and exact retrieved-runbook grounding. This is advisory:
                        you do not authorize, score, or execute infrastructure changes.
                        """)
                .user("Incident: " + request + "\nEvidence: " + evidence + "\nProposal: " + proposal)
                .call()
                .entity(ProposalEvaluation.class);
    }
}
