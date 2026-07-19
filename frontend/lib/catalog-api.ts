export type PageView<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type TeamView = {
  id: string;
  name: string;
  contactChannel: string;
  archivedAt: string | null;
  version: number;
};

export type ServiceView = {
  id: string;
  name: string;
  ownerTeamId: string;
  ownerTeamName: string;
  tier: "CRITICAL" | "STANDARD";
  allowedActions: ActionType[];
  archivedAt: string | null;
  version: number;
};

export type DependencyView = {
  id: string;
  callerServiceId: string;
  callerServiceName: string;
  dependencyServiceId: string;
  dependencyServiceName: string;
  criticality: "REQUIRED" | "DEGRADED_OK";
  createdAt: string;
  version: number;
};

export type RunbookView = {
  id: string;
  title: string;
  symptomDescription: string;
  steps: string;
  actionType: ActionType;
  archivedAt: string | null;
  version: number;
};

export type ActionType = "RESTART_SERVICE" | "ROLLBACK_DEPLOYMENT" | "SCALE_OUT" | "CLEAR_CACHE";

export type ScenarioView = {
  id: string;
  scenarioKey: string;
  displayName: string;
  description: string;
  scenarioType: "BAD_DEPLOY" | "DEPENDENCY_TIMEOUT" | "CAPACITY_SATURATION" | "CACHE_STALENESS";
  serviceId: string;
  serviceName: string;
  severity: "SEV1" | "SEV2" | "SEV3" | "SEV4";
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  archivedAt: string | null;
  version: number;
};

const ROOT = "/api/v1/catalog";

async function request<T>(token: string, path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${ROOT}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
    cache: "no-store",
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(problem?.message ?? `Catalog API returned ${response.status}`);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

export async function loadCatalog(token: string) {
  const query = "?size=100&includeArchived=true";
  const [teams, services, dependencies, runbooks, scenarios] = await Promise.all([
    request<PageView<TeamView>>(token, `/teams${query}`),
    request<PageView<ServiceView>>(token, `/services${query}`),
    request<PageView<DependencyView>>(token, "/dependencies?size=100"),
    request<PageView<RunbookView>>(token, `/runbooks${query}`),
    request<PageView<ScenarioView>>(token, `/scenarios${query}`),
  ]);
  return { teams, services, dependencies, runbooks, scenarios };
}

export const catalogApi = {
  saveTeam: (token: string, value: Omit<TeamView, "id" | "archivedAt">, id?: string) =>
    request<TeamView>(token, id ? `/teams/${id}` : "/teams", {
      method: id ? "PUT" : "POST", body: JSON.stringify(value),
    }),
  archiveTeam: (token: string, value: TeamView) =>
    request<void>(token, `/teams/${value.id}?version=${value.version}`, { method: "DELETE" }),
  saveService: (token: string, value: Omit<ServiceView, "id" | "ownerTeamName" | "archivedAt">, id?: string) =>
    request<ServiceView>(token, id ? `/services/${id}` : "/services", {
      method: id ? "PUT" : "POST", body: JSON.stringify(value),
    }),
  archiveService: (token: string, value: ServiceView) =>
    request<void>(token, `/services/${value.id}?version=${value.version}`, { method: "DELETE" }),
  createDependency: (token: string, value: Pick<DependencyView, "callerServiceId" | "dependencyServiceId" | "criticality">) =>
    request<DependencyView>(token, "/dependencies", { method: "POST", body: JSON.stringify(value) }),
  deleteDependency: (token: string, value: DependencyView) =>
    request<void>(token, `/dependencies/${value.id}?version=${value.version}`, { method: "DELETE" }),
  saveRunbook: (token: string, value: Omit<RunbookView, "id" | "archivedAt">, id?: string) =>
    request<RunbookView>(token, id ? `/runbooks/${id}` : "/runbooks", {
      method: id ? "PUT" : "POST", body: JSON.stringify(value),
    }),
  archiveRunbook: (token: string, value: RunbookView) =>
    request<void>(token, `/runbooks/${value.id}?version=${value.version}`, { method: "DELETE" }),
  saveScenario: (token: string, value: Omit<ScenarioView, "id" | "serviceName" | "createdAt" | "updatedAt" | "archivedAt">, id?: string) =>
    request<ScenarioView>(token, id ? `/scenarios/${id}` : "/scenarios", {
      method: id ? "PUT" : "POST", body: JSON.stringify(value),
    }),
  archiveScenario: (token: string, value: ScenarioView) =>
    request<void>(token, `/scenarios/${value.id}?version=${value.version}`, { method: "DELETE" }),
};
