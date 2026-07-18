package io.mofazzal.sentinel.guardrail;

public final class TestGateAuthorizationFactory {

    private TestGateAuthorizationFactory() {
    }

    public static GateEvaluation evaluate(GuardrailGate gate, GateRequest request) {
        return gate.evaluate(request);
    }
}
