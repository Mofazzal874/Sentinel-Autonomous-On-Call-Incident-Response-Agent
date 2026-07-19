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
  ledger: LedgerEntry[];
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

export function getDemoRun(publicId: string, signal?: AbortSignal) {
  return request<DemoRun>(`${API_ROOT}/${encodeURIComponent(publicId)}`, signal);
}
