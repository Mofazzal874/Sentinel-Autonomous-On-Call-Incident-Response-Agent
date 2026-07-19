import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
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
    listDemoRuns: vi.fn(async () => summaries),
    getDemoRun: vi.fn(async (id: string) => details.get(id)),
  };
});

afterEach(cleanup);

describe("operator console", () => {
  it("loads persisted summaries and renders the selected investigation", async () => {
    render(<OperatorConsole />);

    expect(await screen.findByText("Capacity change awaiting approval")).toBeInTheDocument();
    expect(screen.getByText("Faulty payment release")).toBeInTheDocument();
    expect(await screen.findByText("Scale Out")).toBeInTheDocument();
    expect(screen.getByText("92%")).toBeInTheDocument();
    expect(screen.getByText("Approval Requested")).toBeInTheDocument();
  });

  it("switches to an ungrounded incident without inventing a proposal", async () => {
    render(<OperatorConsole />);
    const ambiguous = await screen.findByRole("button", { name: /Ambiguous checkout dependency/i });

    fireEvent.click(ambiguous);

    await waitFor(() => expect(screen.getByText(/no grounded remediation/i)).toBeInTheDocument());
    expect(screen.getByText("Escalated to a human operator")).toBeInTheDocument();
    expect(screen.getByText("No infrastructure action was proposed or claimed.")).toBeInTheDocument();
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
    ledger: action ? [{ eventType: status === "DRY_RUN" ? "DRY_RUN" : "APPROVAL_REQUESTED",
      decision: status, mode: status === "DRY_RUN" ? "DRY_RUN" : "NONE", actor: "AGENT",
      details: "Recorded decision", recordedAt: base.startedAt }] : [],
  };
}
