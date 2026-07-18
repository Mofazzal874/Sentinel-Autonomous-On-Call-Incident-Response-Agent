package io.mofazzal.sentinel.agent.config;

import io.mofazzal.sentinel.agent.application.EvidenceCollector;
import io.mofazzal.sentinel.agent.application.AgentRunLifecycleService;
import io.mofazzal.sentinel.agent.application.AgentTriageCoordinator;
import io.mofazzal.sentinel.agent.application.IncidentRouter;
import io.mofazzal.sentinel.agent.application.ProposalEvaluator;
import io.mofazzal.sentinel.agent.application.ProposalGenerator;
import io.mofazzal.sentinel.agent.application.TranscriptRecorder;
import io.mofazzal.sentinel.agent.application.TriageWorkflow;
import io.mofazzal.sentinel.guardrail.RemediationDecisionCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sentinel.agent.enabled", havingValue = "true")
public class AgentWorkflowConfiguration {

    @Bean
    TriageWorkflow triageWorkflow(IncidentRouter router, EvidenceCollector evidenceCollector,
                                  ProposalGenerator generator, ProposalEvaluator evaluator,
                                  TranscriptRecorder transcript, AgentProperties properties) {
        return new TriageWorkflow(router, evidenceCollector, generator, evaluator,
                transcript, properties.maxProposalAttempts());
    }

    @Bean
    AgentTriageCoordinator agentTriageCoordinator(TriageWorkflow workflow,
                                                   AgentRunLifecycleService lifecycle,
                                                   RemediationDecisionCoordinator remediationDecisions) {
        return new AgentTriageCoordinator(workflow, lifecycle, remediationDecisions);
    }
}
