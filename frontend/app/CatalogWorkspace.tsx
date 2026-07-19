"use client";

import { FormEvent, useCallback, useState } from "react";
import {
  ActionType,
  catalogApi,
  DependencyView,
  loadCatalog,
  RunbookView,
  ScenarioView,
  ServiceView,
  TeamView,
} from "../lib/catalog-api";

type CatalogData = Awaited<ReturnType<typeof loadCatalog>>;
type Section = "teams" | "services" | "dependencies" | "runbooks" | "scenarios";

const ACTIONS: ActionType[] = ["RESTART_SERVICE", "ROLLBACK_DEPLOYMENT", "SCALE_OUT", "CLEAR_CACHE"];

export default function CatalogWorkspace() {
  const [token, setToken] = useState("");
  const [data, setData] = useState<CatalogData | null>(null);
  const [section, setSection] = useState<Section>("services");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("Enter a short-lived ADMIN JWT to open the protected workspace.");
  const [editing, setEditing] = useState<TeamView | ServiceView | RunbookView | ScenarioView | null>(null);

  const reload = useCallback(async (credential = token) => {
    if (!credential.trim()) return;
    setBusy(true);
    try {
      setData(await loadCatalog(credential.trim()));
      setMessage("Connected. Changes are persisted in PostgreSQL and guarded by optimistic versions.");
    } catch (error) {
      setData(null);
      setMessage((error as Error).message);
    } finally {
      setBusy(false);
    }
  }, [token]);

  async function mutate(operation: () => Promise<unknown>, success: string) {
    setBusy(true);
    try {
      await operation();
      setEditing(null);
      await reload();
      setMessage(success);
    } catch (error) {
      setMessage((error as Error).message);
      setBusy(false);
    }
  }

  return (
    <section className="catalogWorkspace" aria-label="Catalog administration">
      <header className="catalogHero">
        <div><p className="eyebrow">PROTECTED CONTROL PLANE</p><h2>Fleet &amp; runbook catalog</h2><p>Manage durable operational knowledge without rewriting incident or audit history.</p></div>
        <div className="authCard">
          <label htmlFor="admin-token">Short-lived ADMIN JWT</label>
          <div><input id="admin-token" type="password" value={token} onChange={(event) => setToken(event.target.value)} placeholder="Paste token for this session" /><button disabled={busy || !token.trim()} onClick={() => void reload()}>{busy ? "Checking" : "Connect"}</button></div>
          <small>The token stays only in page memory; it is not stored in the browser.</small>
        </div>
      </header>

      <div className={`catalogNotice ${data ? "success" : ""}`} role="status">{message}</div>

      {data ? <>
        <div className="catalogCounts">
          <CatalogCount label="Teams" value={data.teams.totalItems} />
          <CatalogCount label="Services" value={data.services.totalItems} />
          <CatalogCount label="Dependencies" value={data.dependencies.totalItems} />
          <CatalogCount label="Runbooks" value={data.runbooks.totalItems} />
          <CatalogCount label="Scenarios" value={data.scenarios.totalItems} />
        </div>
        <div className="catalogTabs" role="tablist">
          {(["services", "teams", "dependencies", "runbooks", "scenarios"] as Section[]).map((item) =>
            <button key={item} role="tab" aria-selected={section === item} className={section === item ? "active" : ""} onClick={() => { setSection(item); setEditing(null); }}>{friendly(item)}</button>)}
        </div>
        <div className="catalogGrid">
          <div className="catalogTable panel">
            {section === "teams" && <TeamTable items={data.teams.items} onEdit={setEditing} onArchive={(item) => mutate(() => catalogApi.archiveTeam(token, item), "Team archived; historical references remain intact.")} />}
            {section === "services" && <ServiceTable items={data.services.items} onEdit={setEditing} onArchive={(item) => mutate(() => catalogApi.archiveService(token, item), "Service archived; incidents and telemetry were preserved.")} />}
            {section === "dependencies" && <DependencyTable items={data.dependencies.items} onDelete={(item) => mutate(() => catalogApi.deleteDependency(token, item), "Dependency edge deleted.")} />}
            {section === "runbooks" && <RunbookTable items={data.runbooks.items} onEdit={setEditing} onArchive={(item) => mutate(() => catalogApi.archiveRunbook(token, item), "Runbook archived and removed from semantic retrieval.")} />}
            {section === "scenarios" && <ScenarioTable items={data.scenarios.items} onEdit={setEditing} onArchive={(item) => mutate(() => catalogApi.archiveScenario(token, item), "Scenario template disabled and archived.")} />}
          </div>
          <div className="catalogEditor panel">
            {section === "teams" && <TeamForm key={(editing as TeamView | null)?.id ?? "new"} item={editing as TeamView | null} onSave={(value, id) => mutate(() => catalogApi.saveTeam(token, value, id), id ? "Team updated." : "Team created with a generated database ID.")} />}
            {section === "services" && <ServiceForm key={(editing as ServiceView | null)?.id ?? "new"} item={editing as ServiceView | null} teams={data.teams.items.filter((item) => !item.archivedAt)} onSave={(value, id) => mutate(() => catalogApi.saveService(token, value, id), id ? "Service updated." : "Service created with a generated database ID.")} />}
            {section === "dependencies" && <DependencyForm services={data.services.items.filter((item) => !item.archivedAt)} onSave={(value) => mutate(() => catalogApi.createDependency(token, value), "Dependency edge created.")} />}
            {section === "runbooks" && <RunbookForm key={(editing as RunbookView | null)?.id ?? "new"} item={editing as RunbookView | null} onSave={(value, id) => mutate(() => catalogApi.saveRunbook(token, value, id), id ? "Runbook updated; re-indexing is required before semantic use." : "Runbook created with a generated database ID.")} />}
            {section === "scenarios" && <ScenarioForm key={(editing as ScenarioView | null)?.id ?? "new"} item={editing as ScenarioView | null} services={data.services.items.filter((item) => !item.archivedAt)} onSave={(value, id) => mutate(() => catalogApi.saveScenario(token, value, id), id ? "Fixed scenario template updated." : "Fixed scenario template created with a generated database ID.")} />}
          </div>
        </div>
      </> : <div className="catalogLocked"><span>LOCKED</span><h3>Administrative mutations require identity.</h3><p>The public incident console remains readable, but catalog changes require a signed ADMIN token. This is an actual backend authorization boundary, not a hidden frontend button.</p></div>}
    </section>
  );
}

function CatalogCount({ label, value }: { label: string; value: number }) { return <div><span>{label}</span><strong>{value}</strong></div>; }

function TeamTable({ items, onEdit, onArchive }: { items: TeamView[]; onEdit: (item: TeamView) => void; onArchive: (item: TeamView) => void }) {
  return <CatalogList title="Teams">{items.map((item) => <CatalogRow key={item.id} title={item.name} detail={item.contactChannel} item={item} onEdit={() => onEdit(item)} onRemove={() => onArchive(item)} removeLabel="Archive" />)}</CatalogList>;
}
function ServiceTable({ items, onEdit, onArchive }: { items: ServiceView[]; onEdit: (item: ServiceView) => void; onArchive: (item: ServiceView) => void }) {
  return <CatalogList title="Services">{items.map((item) => <CatalogRow key={item.id} title={item.name} detail={`${item.ownerTeamName} / ${item.tier}`} item={item} onEdit={() => onEdit(item)} onRemove={() => onArchive(item)} removeLabel="Archive" />)}</CatalogList>;
}
function DependencyTable({ items, onDelete }: { items: DependencyView[]; onDelete: (item: DependencyView) => void }) {
  return <CatalogList title="Dependency graph">{items.map((item) => <CatalogRow key={item.id} title={`${item.callerServiceName} -> ${item.dependencyServiceName}`} detail={friendly(item.criticality)} item={item} onRemove={() => onDelete(item)} removeLabel="Delete" />)}</CatalogList>;
}
function RunbookTable({ items, onEdit, onArchive }: { items: RunbookView[]; onEdit: (item: RunbookView) => void; onArchive: (item: RunbookView) => void }) {
  return <CatalogList title="Runbooks">{items.map((item) => <CatalogRow key={item.id} title={item.title} detail={friendly(item.actionType)} item={item} onEdit={() => onEdit(item)} onRemove={() => onArchive(item)} removeLabel="Archive" />)}</CatalogList>;
}
function ScenarioTable({ items, onEdit, onArchive }: { items: ScenarioView[]; onEdit: (item: ScenarioView) => void; onArchive: (item: ScenarioView) => void }) {
  return <CatalogList title="Fixed scenarios">{items.map((item) => <CatalogRow key={item.id} title={item.displayName} detail={`${friendly(item.scenarioType)} / ${item.serviceName} / ${item.severity}`} item={item} onEdit={() => onEdit(item)} onRemove={() => onArchive(item)} removeLabel="Archive" />)}</CatalogList>;
}

function CatalogList({ title, children }: { title: string; children: React.ReactNode }) { return <><div className="panelHead"><div><span className="sectionNumber">DB</span><h3>{title}</h3></div></div><div className="catalogRows">{children}</div></>; }
function CatalogRow({ title, detail, item, onEdit, onRemove, removeLabel }: { title: string; detail: string; item: { id: string; version: number; archivedAt?: string | null }; onEdit?: () => void; onRemove: () => void; removeLabel: string }) {
  return <article className={item.archivedAt ? "archived" : ""}><div><strong>{title}</strong><span>{detail}</span><code>{item.id}</code></div><small>v{item.version}{item.archivedAt ? " / archived" : ""}</small><div>{onEdit && !item.archivedAt && <button onClick={onEdit}>Edit</button>}{!item.archivedAt && <button className="dangerButton" onClick={onRemove}>{removeLabel}</button>}</div></article>;
}

function TeamForm({ item, onSave }: { item: TeamView | null; onSave: (value: Omit<TeamView, "id" | "archivedAt">, id?: string) => void }) {
  return <CatalogForm title={item ? "Edit team" : "Create team"} onSubmit={(form) => onSave({ name: text(form, "name"), contactChannel: text(form, "contactChannel"), version: item?.version ?? 0 }, item?.id)}><label>Name<input name="name" defaultValue={item?.name} required maxLength={100} /></label><label>Contact channel<input name="contactChannel" defaultValue={item?.contactChannel} required maxLength={200} /></label></CatalogForm>;
}
function ServiceForm({ item, teams, onSave }: { item: ServiceView | null; teams: TeamView[]; onSave: (value: Omit<ServiceView, "id" | "ownerTeamName" | "archivedAt">, id?: string) => void }) {
  return <CatalogForm title={item ? "Edit service" : "Create service"} onSubmit={(form) => onSave({ name: text(form, "name"), ownerTeamId: text(form, "ownerTeamId"), tier: text(form, "tier") as ServiceView["tier"], allowedActions: form.getAll("allowedActions") as ActionType[], version: item?.version ?? 0 }, item?.id)}><label>Name<input name="name" defaultValue={item?.name} required /></label><label>Owner<select name="ownerTeamId" defaultValue={item?.ownerTeamId} required><option value="">Select team</option>{teams.map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}</select></label><label>Tier<select name="tier" defaultValue={item?.tier ?? "STANDARD"}><option>STANDARD</option><option>CRITICAL</option></select></label><fieldset><legend>Allowlisted actions</legend>{ACTIONS.map((action) => <label className="checkLabel" key={action}><input type="checkbox" name="allowedActions" value={action} defaultChecked={item?.allowedActions.includes(action)} />{friendly(action)}</label>)}</fieldset></CatalogForm>;
}
function DependencyForm({ services, onSave }: { services: ServiceView[]; onSave: (value: Pick<DependencyView, "callerServiceId" | "dependencyServiceId" | "criticality">) => void }) {
  return <CatalogForm title="Create dependency" onSubmit={(form) => onSave({ callerServiceId: text(form, "callerServiceId"), dependencyServiceId: text(form, "dependencyServiceId"), criticality: text(form, "criticality") as DependencyView["criticality"] })}><label>Caller<select name="callerServiceId" required><option value="">Select service</option>{services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label><label>Dependency<select name="dependencyServiceId" required><option value="">Select service</option>{services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label><label>Criticality<select name="criticality"><option>REQUIRED</option><option>DEGRADED_OK</option></select></label></CatalogForm>;
}
function RunbookForm({ item, onSave }: { item: RunbookView | null; onSave: (value: Omit<RunbookView, "id" | "archivedAt">, id?: string) => void }) {
  return <CatalogForm title={item ? "Edit runbook" : "Create runbook"} onSubmit={(form) => onSave({ title: text(form, "title"), symptomDescription: text(form, "symptomDescription"), steps: text(form, "steps"), actionType: text(form, "actionType") as ActionType, version: item?.version ?? 0 }, item?.id)}><label>Title<input name="title" defaultValue={item?.title} required /></label><label>Symptoms<textarea name="symptomDescription" defaultValue={item?.symptomDescription} required /></label><label>Bounded steps<textarea name="steps" defaultValue={item?.steps} required /></label><label>Action<select name="actionType" defaultValue={item?.actionType}>{ACTIONS.map((action) => <option key={action}>{action}</option>)}</select></label></CatalogForm>;
}
function ScenarioForm({ item, services, onSave }: { item: ScenarioView | null; services: ServiceView[]; onSave: (value: Omit<ScenarioView, "id" | "serviceName" | "createdAt" | "updatedAt" | "archivedAt">, id?: string) => void }) {
  return <CatalogForm title={item ? "Edit fixed scenario" : "Create fixed scenario"} onSubmit={(form) => onSave({ scenarioKey: text(form, "scenarioKey"), displayName: text(form, "displayName"), description: text(form, "description"), scenarioType: text(form, "scenarioType") as ScenarioView["scenarioType"], serviceId: text(form, "serviceId"), severity: text(form, "severity") as ScenarioView["severity"], enabled: form.get("enabled") === "on", version: item?.version ?? 0 }, item?.id)}><label>Stable key<input name="scenarioKey" defaultValue={item?.scenarioKey} required /></label><label>Display name<input name="displayName" defaultValue={item?.displayName} required /></label><label>Description<textarea name="description" defaultValue={item?.description} required /></label><label>Fixed failure type<select name="scenarioType" defaultValue={item?.scenarioType}>{["BAD_DEPLOY", "DEPENDENCY_TIMEOUT", "CAPACITY_SATURATION", "CACHE_STALENESS"].map((type) => <option key={type}>{type}</option>)}</select></label><label>Target service<select name="serviceId" defaultValue={item?.serviceId} required><option value="">Select service</option>{services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label><label>Severity<select name="severity" defaultValue={item?.severity ?? "SEV2"}>{["SEV1", "SEV2", "SEV3", "SEV4"].map((severity) => <option key={severity}>{severity}</option>)}</select></label><label className="checkLabel"><input type="checkbox" name="enabled" defaultChecked={item?.enabled ?? true} />Available for sandbox execution</label></CatalogForm>;
}
function CatalogForm({ title, onSubmit, children }: { title: string; onSubmit: (data: FormData) => void; children: React.ReactNode }) {
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); onSubmit(new FormData(event.currentTarget)); }
  return <form className="catalogForm" onSubmit={submit}><div className="panelHead"><div><span className="sectionNumber">WRITE</span><h3>{title}</h3></div></div><div className="formBody">{children}<button className="primaryButton" type="submit">Save to PostgreSQL</button></div></form>;
}
function text(form: FormData, name: string) { return String(form.get(name) ?? "").trim(); }
function friendly(value: string) { return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (character) => character.toUpperCase()); }
