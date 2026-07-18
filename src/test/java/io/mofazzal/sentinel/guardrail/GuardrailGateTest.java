package io.mofazzal.sentinel.guardrail;

import io.mofazzal.sentinel.fleet.domain.RemediationActionType;
import io.mofazzal.sentinel.fleet.domain.ServiceTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardrailGateTest {

    private final KillSwitch killSwitch = mock(KillSwitch.class);
    private final ServiceActionAllowlist allowlist = mock(ServiceActionAllowlist.class);
    private final DeterministicRiskScorer riskScorer = mock(DeterministicRiskScorer.class);
    private final ActionHistory actionHistory = mock(ActionHistory.class);
    private final DryRunPolicy dryRunPolicy = mock(DryRunPolicy.class);
    private final UUID serviceId = UUID.randomUUID();
    private final GateRequest lowRiskRequest = request(false);
    private final RiskBreakdown lowRisk = new RiskBreakdown(1, 1, 0, 0, 0, 2);
    private GuardrailGate gate;

    @BeforeEach
    void setUp() {
        gate = new GuardrailGate(killSwitch, allowlist, riskScorer, actionHistory, dryRunPolicy, 6);
        when(allowlist.permits(serviceId, RemediationActionType.RESTART_SERVICE)).thenReturn(true);
        when(riskScorer.score(lowRiskRequest.riskFacts())).thenReturn(lowRisk);
    }

    @Test
    void killSwitchStopsBeforeEveryOtherPolicy() {
        when(killSwitch.isEngaged()).thenReturn(true);

        assertThat(gate.decide(lowRiskRequest).type()).isEqualTo(GateDecisionType.ESCALATE);

        verify(allowlist, never()).permits(serviceId, RemediationActionType.RESTART_SERVICE);
        verify(riskScorer, never()).score(lowRiskRequest.riskFacts());
        verify(actionHistory, never()).alreadyActiveOrApplied(lowRiskRequest.incidentFingerprint(), RemediationActionType.RESTART_SERVICE);
        verify(dryRunPolicy, never()).isEnabled(serviceId);
    }

    @Test
    void killSwitchFailureIsFailClosed() {
        when(killSwitch.isEngaged()).thenThrow(new IllegalStateException("database unavailable"));

        GateDecision decision = gate.decide(lowRiskRequest);

        assertThat(decision.type()).isEqualTo(GateDecisionType.ESCALATE);
        assertThat(decision.reason()).contains("unavailable");
        verify(allowlist, never()).permits(serviceId, RemediationActionType.RESTART_SERVICE);
    }

    @Test
    void nonAllowlistedActionIsRefusedBeforeScoring() {
        when(allowlist.permits(serviceId, RemediationActionType.RESTART_SERVICE)).thenReturn(false);

        assertThat(gate.decide(lowRiskRequest).type()).isEqualTo(GateDecisionType.REFUSE);

        verify(riskScorer, never()).score(lowRiskRequest.riskFacts());
        verify(actionHistory, never()).alreadyActiveOrApplied(lowRiskRequest.incidentFingerprint(), RemediationActionType.RESTART_SERVICE);
    }

    @Test
    void duplicateIsSkippedBeforeDryRunOrExecutionEligibility() {
        when(actionHistory.alreadyActiveOrApplied(
                lowRiskRequest.incidentFingerprint(), RemediationActionType.RESTART_SERVICE)).thenReturn(true);

        GateDecision decision = gate.decide(lowRiskRequest);

        assertThat(decision.type()).isEqualTo(GateDecisionType.SKIP);
        assertThat(decision.riskBreakdown()).contains(lowRisk);
        verify(dryRunPolicy, never()).isEnabled(serviceId);
    }

    @Test
    void dryRunWinsBeforeAutomaticOrApprovalOutcome() {
        when(dryRunPolicy.isEnabled(serviceId)).thenReturn(true);

        assertThat(gate.decide(lowRiskRequest).type()).isEqualTo(GateDecisionType.DRY_RUN);
    }

    @Test
    void lowRiskActionBecomesEligibleForAutomaticExecution() {
        GateDecision decision = gate.decide(lowRiskRequest);

        assertThat(decision.type()).isEqualTo(GateDecisionType.AUTO_EXECUTE);
        assertThat(decision.riskBreakdown()).contains(lowRisk);
    }

    @Test
    void thresholdIsInclusive() {
        RiskBreakdown boundary = new RiskBreakdown(1, 1, 1, 3, 0, 6);
        when(riskScorer.score(lowRiskRequest.riskFacts())).thenReturn(boundary);

        assertThat(gate.decide(lowRiskRequest).type()).isEqualTo(GateDecisionType.AUTO_EXECUTE);
    }

    @Test
    void highRiskActionRequiresHumanApproval() {
        RiskBreakdown highRisk = new RiskBreakdown(4, 4, 3, 3, 2, 16);
        when(riskScorer.score(lowRiskRequest.riskFacts())).thenReturn(highRisk);

        assertThat(gate.decide(lowRiskRequest).type()).isEqualTo(GateDecisionType.REQUIRE_APPROVAL);
    }

    @Test
    void humanApprovalStillRechecksAllEarlierPolicies() {
        GateRequest approved = request(true);
        RiskBreakdown highRisk = new RiskBreakdown(4, 4, 3, 3, 2, 16);
        when(riskScorer.score(approved.riskFacts())).thenReturn(highRisk);

        GateDecision decision = gate.decide(approved);

        assertThat(decision.type()).isEqualTo(GateDecisionType.APPROVED_EXECUTE);
        InOrder order = inOrder(killSwitch, allowlist, riskScorer, actionHistory, dryRunPolicy);
        order.verify(killSwitch).isEngaged();
        order.verify(allowlist).permits(serviceId, RemediationActionType.RESTART_SERVICE);
        order.verify(riskScorer).score(approved.riskFacts());
        order.verify(actionHistory).alreadyActiveOrApplied(
                approved.incidentFingerprint(), RemediationActionType.RESTART_SERVICE);
        order.verify(dryRunPolicy).isEnabled(serviceId);
    }

    private GateRequest request(boolean approvedByHuman) {
        return new GateRequest(
                UUID.randomUUID(),
                serviceId,
                "incident-fingerprint",
                new RiskFacts(RemediationActionType.RESTART_SERVICE, ServiceTier.STANDARD, 0, 0.9, false),
                approvedByHuman);
    }
}
