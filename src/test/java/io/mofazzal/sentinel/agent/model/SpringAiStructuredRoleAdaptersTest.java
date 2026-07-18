package io.mofazzal.sentinel.agent.model;

import io.mofazzal.sentinel.agent.application.ModelCallBudget;
import io.mofazzal.sentinel.agent.domain.Classification;
import io.mofazzal.sentinel.agent.domain.EvidenceBundle;
import io.mofazzal.sentinel.agent.domain.EvidenceSignal;
import io.mofazzal.sentinel.agent.domain.IncidentType;
import io.mofazzal.sentinel.agent.domain.TriageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiStructuredRoleAdaptersTest {

    @Test
    void mapsScriptedModelJsonIntoTypedRoleOutputsAndChargesEveryCall() {
        ScriptedChatModel model = new ScriptedChatModel(List.of(
                """
                {"type":"BAD_DEPLOY","rationale":"The degradation follows a release"}
                """,
                """
                {"actionType":"ROLLBACK_DEPLOYMENT","runbookTitle":"Rollback a faulty service deployment",
                 "steps":["Confirm correlation","Propose rollback"],
                 "rationale":"The release matches the spike","riskNotes":"Critical service rollback"}
                """,
                "{" + "\"passed\":true,\"feedback\":\"Grounded and bounded\"}"
        ));
        AtomicInteger charged = new AtomicInteger();
        ModelCallBudget budget = (incident, role) -> charged.incrementAndGet();
        ChatClient.Builder builder = ChatClient.builder(model);
        var router = new SpringAiIncidentRouter(builder, budget);
        var generator = new SpringAiProposalGenerator(builder, budget);
        var evaluator = new SpringAiProposalEvaluator(builder, budget);
        TriageRequest request = new TriageRequest(UUID.randomUUID(), "payments-api",
                "Errors rose after release", Instant.parse("2026-07-15T12:05:00Z"));

        Classification classification = router.classify(request);
        var proposal = generator.propose(request, classification,
                new EvidenceBundle(List.of(), List.of(), List.of(), List.of()), "No prior critique");
        var evaluation = evaluator.evaluate(request,
                new EvidenceBundle(List.of(), List.of(), List.of(), List.of()), proposal);

        assertThat(classification.type()).isEqualTo(IncidentType.BAD_DEPLOY);
        assertThat(classification.relevantSignals()).containsExactly(
                EvidenceSignal.DEPLOYMENTS, EvidenceSignal.METRICS, EvidenceSignal.RUNBOOKS);
        assertThat(proposal.runbookTitle()).contains("Rollback");
        assertThat(evaluation.passed()).isTrue();
        assertThat(charged).hasValue(3);
        assertThat(model.calls).hasValue(3);
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Queue<String> responses;
        private final AtomicInteger calls = new AtomicInteger();

        private ScriptedChatModel(List<String> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(responses.remove()))));
        }
    }
}
