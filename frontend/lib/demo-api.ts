export type DemoRunSummary = {
  publicId: string;
  scenarioKey: string;
  scenarioTitle: string;
  summary: string;
  source: "RECORDED" | "LIVE";
  service: string;
  severity: "SEV1" | "SEV2" | "SEV3" | "SEV4";
  incidentStatus: string;
  startedAt: string;
};

export type TimelineEntry = {
  sequence: number;
  type: "CLASSIFICATION" | "EVIDENCE" | "PROPOSAL" | "CRITIQUE" | "OUTCOME";
  iteration: number;
  content: string;
  recordedAt: string;
};

export type RemediationView = {
  action: string;
  runbook: string;
  steps: string[];
  rationale: string;
  riskNotes: string;
  groundingSimilarity: number;
  riskScore: number | null;
  status: string;
  decisionNote: string | null;
};

export type LedgerEntry = {
  eventType: string;
  decision: string | null;
  mode: string;
  actor: string;
  details: string;
  recordedAt: string;
};

export type DemoRun = DemoRunSummary & {
  disclaimer: string;
  timeline: TimelineEntry[];
  remediation: RemediationView | null;
  evidence: {
    deployments: { version: string; gitSha: string; status: string; deployedBy: string; deployedAt: string }[];
    metrics: { metric: string; points: { value: number; recordedAt: string }[] }[];
    logs: { level: string; message: string; traceId: string; occurredAt: string }[];
    runbooks: { title: string; symptom: string; action: string; steps: string[] }[];
  };
  ledger: LedgerEntry[];
};

export type DemoChoice = { value: string; label: string; description: string };
export type DemoInvestigationOptions = {
  services: { id: string; name: string; team: string; tier: string; allowedActions: string[] }[];
  symptoms: DemoChoice[];
  severities: DemoChoice[];
  signalIntensities: DemoChoice[];
  customerImpacts: DemoChoice[];
  deploymentContexts: DemoChoice[];
  evidencePlan: { metricSeries: number; samplesPerSeries: number; logEvents: number; persistence: string; executionMode: string };
};
export type DemoInvestigationRequest = {
  serviceId: string; symptom: string; severity: string; signalIntensity: string;
  customerImpact: string; deploymentContext: string;
};

export type DemoScenario = {
  id: string;
  scenarioKey: string;
  displayName: string;
  description: string;
  scenarioType: string;
  service: string;
  severity: DemoRunSummary["severity"];
};

export type DemoSubmission = {
  publicId: string;
  scenarioKey: string;
  scenarioTitle: string;
  state: "ACCEPTED" | "QUEUED" | "COMPLETED" | "FAILED";
  incidentStatus: string | null;
  submittedAt: string;
  completedAt: string | null;
  failureReason: string | null;
  runUrl: string;
};

export type DemoSystemOverview = {
  teams: number;
  services: number;
  dependencies: number;
  deployments: number;
  metricSamples: number;
  logEvents: number;
  incidents: number;
  runbooks: number;
  publicScenarios: number;
  liveRuns: number;
  ledgerEvents: number;
  executionMode: "DRY_RUN";
  modelAuthority: "PROPOSE_ONLY";
  measuredAt: string;
};

const API_ROOT = "/api/v1/demo/runs";

async function request<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, {
    signal,
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`Sentinel API returned ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function listDemoRuns(signal?: AbortSignal) {
  return request<DemoRunSummary[]>(API_ROOT, signal);
}

export function getDemoOverview(signal?: AbortSignal) {
  return request<DemoSystemOverview>("/api/v1/demo/overview", signal);
}

export function getDemoRun(publicId: string, signal?: AbortSignal) {
  return request<DemoRun>(`${API_ROOT}/${encodeURIComponent(publicId)}`, signal);
}

export function listDemoScenarios(signal?: AbortSignal) {
  return request<DemoScenario[]>("/api/v1/demo/scenarios", signal);
}

export function getDemoInvestigationOptions(signal?: AbortSignal) {
  return request<DemoInvestigationOptions>("/api/v1/demo/investigation-options", signal);
}

export async function submitDemoInvestigation(payload: DemoInvestigationRequest, idempotencyKey: string) {
  const response = await fetch("/api/v1/demo/investigations", {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(problem?.message ?? `Investigation API returned ${response.status}`);
  }
  return response.json() as Promise<DemoSubmission>;
}

export async function submitDemoScenario(templateId: string, idempotencyKey: string) {
  const response = await fetch(`/api/v1/demo/scenarios/${encodeURIComponent(templateId)}/runs`, {
    method: "POST",
    headers: { Accept: "application/json", "Idempotency-Key": idempotencyKey },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(problem?.message ?? `Scenario API returned ${response.status}`);
  }
  return response.json() as Promise<DemoSubmission>;
}

export function getDemoSubmission(publicId: string, signal?: AbortSignal) {
  return request<DemoSubmission>(`/api/v1/demo/submissions/${encodeURIComponent(publicId)}`, signal);
}
