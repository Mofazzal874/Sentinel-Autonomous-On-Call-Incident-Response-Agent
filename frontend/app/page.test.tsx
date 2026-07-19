import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OperatorConsole from "./page";
import { DemoRun, DemoRunSummary } from "../lib/demo-api";

const summaries: DemoRunSummary[] = [
  summary("run-approval", "capacity-approval", "Capacity change awaiting approval", "catalog-api", "SEV2", "AWAITING_APPROVAL"),
  summary("run-ambiguous", "ambiguous-dependency", "Ambiguous checkout dependency", "checkout-web", "SEV2", "ESCALATED"),
  summary("run-faulty", "faulty-deployment", "Faulty payment release", "payments-api", "SEV1", "ESCALATED"),
];

const details = new Map<string, DemoRun>([
  ["run-approval", detail(summaries[0], "SCALE_OUT", "AWAITING_APPROVAL")],
  ["run-ambiguous", { ...detail(summaries[1], null, null), remediation: null, ledger: [] }],
  ["run-faulty", detail(summaries[2], "ROLLBACK_DEPLOYMENT", "DRY_RUN")],
]);

vi.mock("../lib/demo-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/demo-api")>();
  return {
    ...actual,
    getDemoOverview: vi.fn(async () => ({ teams: 4, services: 12, dependencies: 18, deployments: 60,
      metricSamples: 10800, logEvents: 1080, incidents: 30, runbooks: 10, publicScenarios: 4,
      liveRuns: 0, ledgerEvents: 54, executionMode: "DRY_RUN", modelAuthority: "PROPOSE_ONLY",
      measuredAt: "2026-07-19T00:00:00Z" })),
    listDemoRuns: vi.fn(async () => summaries),
    getDemoRun: vi.fn(async (id: string) => details.get(id)),
    getDemoInvestigationOptions: vi.fn(async () => ({
      services: [{ id: "service-1", name: "payments-api", team: "payments", tier: "TIER_1", allowedActions: ["ROLLBACK_DEPLOYMENT"] }],
      symptoms: [{ value: "BAD_DEPLOY", label: "Release regression", description: "Errors rose after deployment." }],
      severities: [{ value: "SEV2", label: "SEV2 · Major", description: "Major impact." }],
      signalIntensities: [{ value: "HIGH", label: "High", description: "Strong signals." }],
      customerImpacts: [{ value: "PARTIAL_OUTAGE", label: "Partial outage", description: "Some requests fail." }],
      deploymentContexts: [{ value: "RECENT_CHANGE", label: "Recent deployment", description: "A release is present." }],
      evidencePlan: { metricSeries: 5, samplesPerSeries: 12, logEvents: 8, persistence: "PostgreSQL", executionMode: "DRY_RUN" },
    })),
    submitDemoInvestigation: vi.fn(async () => ({ publicId: "live-run-1", scenarioKey: "custom-bad_deploy",
      scenarioTitle: "Faulty payment release", state: "COMPLETED", incidentStatus: "ESCALATED",
      submittedAt: "2026-07-19T00:00:00Z", completedAt: "2026-07-19T00:00:10Z",
      failureReason: null, runUrl: "/api/v1/demo/runs/live-run-1" })),
    getDemoSubmission: vi.fn(),
  };
});

afterEach(cleanup);
beforeEach(() => { window.scrollTo = vi.fn(); });

describe("operator console", () => {
  it("loads persisted summaries and renders the selected investigation", async () => {
    render(<OperatorConsole />);

    expect(await screen.findByText("10,800")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Incidents/i }));
    expect((await screen.findAllByText("Capacity change awaiting approval")).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("button", { name: "Safety decision" }));
    expect(await screen.findByText("Scale Out")).toBeInTheDocument();
    expect(screen.getByText("92%")).toBeInTheDocument();
    expect(screen.getAllByText("Awaiting Approval").length).toBeGreaterThan(0);
  });

  it("switches to an ungrounded incident without inventing a proposal", async () => {
    render(<OperatorConsole />);
    fireEvent.click(screen.getByRole("button", { name: /Incidents/i }));
    const ambiguousTitle = await screen.findByText("Ambiguous checkout dependency");
    const ambiguous = ambiguousTitle.closest("button");

    expect(ambiguous).not.toBeNull();
    fireEvent.click(ambiguous!);
    fireEvent.click(await screen.findByRole("button", { name: "Safety decision" }));

    await waitFor(() => expect(screen.getByText(/No grounded remediation was allowed/i)).toBeInTheDocument());
    expect(screen.getByText(/hard safety stop/i)).toBeInTheDocument();
  });

  it("shows a real authentication boundary before catalog administration", async () => {
    render(<OperatorConsole />);
    fireEvent.click(screen.getByRole("button", { name: "Admin" }));

    expect(await screen.findByText("Administrative mutations require identity.")).toBeInTheDocument();
    expect(screen.getByLabelText("Short-lived ADMIN JWT")).toBeInTheDocument();
    expect(screen.getByText(/not a hidden frontend button/i)).toBeInTheDocument();
  });

  it("teaches a first-time visitor what is real and how to use the system", async () => {
    render(<OperatorConsole />);
    fireEvent.click(screen.getByRole("button", { name: "Learn" }));

    expect(await screen.findByText(/Learn the system by/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Understand the AI/i }));
    expect(screen.getByText(/organize and critique a proposal/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Read the raw evidence/i }));
    expect(screen.getByText(/A conclusion is defensible/i)).toBeInTheDocument();
  });

  it("creates a configured incident and offers its persisted report", async () => {
    render(<OperatorConsole />);
    fireEvent.click(screen.getByRole("button", { name: /^Live lab$/i }));
    const button = await screen.findByRole("button", { name: "Create investigation" });
    await waitFor(() => expect(button).toBeEnabled());

    fireEvent.click(button);

    const report = await screen.findByRole("button", { name: /Open evidence-backed report/i });
    fireEvent.click(report);
    expect(await screen.findByText("Incident explorer")).toBeInTheDocument();
  });
});

function summary(publicId: string, scenarioKey: string, scenarioTitle: string, service: string,
                 severity: DemoRunSummary["severity"], incidentStatus: string): DemoRunSummary {
  return { publicId, scenarioKey, scenarioTitle, summary: "Persistent scenario summary.",
    source: "RECORDED", service, severity,
    incidentStatus, startedAt: "2026-07-18T20:41:18Z" };
}

function detail(base: DemoRunSummary, action: string | null, status: string | null): DemoRun {
  return {
    ...base,
    disclaimer: "Deterministic synthetic operations data; no customer or production data.",
    timeline: [{ sequence: 1, type: "CLASSIFICATION", iteration: 0,
      content: "Evidence-backed classification", recordedAt: base.startedAt }],
    remediation: action ? {
      action,
      runbook: "Bounded operational response",
      steps: ["Verify evidence", "Apply one bounded change"],
      rationale: "The evidence supports this action.",
      riskNotes: "Deterministic gate remains authoritative.",
      groundingSimilarity: 0.92,
      riskScore: 10,
      status: status!,
      decisionNote: "risk exceeds automatic threshold",
    } : null,
    evidence: { deployments: [], metrics: [], logs: [], runbooks: [] },
    ledger: action ? [{ eventType: status === "DRY_RUN" ? "DRY_RUN" : "APPROVAL_REQUESTED",
      decision: status, mode: status === "DRY_RUN" ? "DRY_RUN" : "NONE", actor: "AGENT",
      details: "Recorded decision", recordedAt: base.startedAt }] : [],
  };
}
