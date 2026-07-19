"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  DemoRun,
  DemoRunSummary,
  getDemoRun,
  listDemoRuns,
} from "../lib/demo-api";
import CatalogWorkspace from "./CatalogWorkspace";

type LoadState = "loading" | "ready" | "error";

export default function OperatorConsole() {
  const [view, setView] = useState<"operations" | "catalog">("operations");
  const [runs, setRuns] = useState<DemoRunSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selected, setSelected] = useState<DemoRun | null>(null);
  const [listState, setListState] = useState<LoadState>("loading");
  const [detailState, setDetailState] = useState<LoadState>("loading");

  const loadRuns = useCallback(async (signal?: AbortSignal) => {
    setListState("loading");
    try {
      const result = await listDemoRuns(signal);
      setRuns(result);
      setSelectedId((current) => current ?? result[0]?.publicId ?? null);
      setListState("ready");
    } catch (error) {
      if ((error as Error).name !== "AbortError") setListState("error");
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void loadRuns(controller.signal);
    return () => controller.abort();
  }, [loadRuns]);

  useEffect(() => {
    if (!selectedId) return;
    const controller = new AbortController();
    setDetailState("loading");
    getDemoRun(selectedId, controller.signal)
      .then((result) => {
        setSelected(result);
        setDetailState("ready");
      })
      .catch((error: Error) => {
        if (error.name !== "AbortError") setDetailState("error");
      });
    return () => controller.abort();
  }, [selectedId]);

  const counts = useMemo(() => ({
    total: runs.length,
    escalated: runs.filter((run) => run.incidentStatus === "ESCALATED").length,
    approvals: runs.filter((run) => run.incidentStatus === "AWAITING_APPROVAL").length,
    critical: runs.filter((run) => run.severity === "SEV1").length,
  }), [runs]);

  return (
    <main className="shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <a className="brand" href="#overview" aria-label="Sentinel home">
          <span className="brandMark">S</span>
          <span>Sentinel</span>
        </a>
        <nav>
          <button className={`navItem ${view === "operations" ? "active" : ""}`} onClick={() => setView("operations")}><span>01</span> Operations</button>
          <a className="navItem" href="#incidents"><span>02</span> Incidents</a>
          <a className="navItem" href="#investigation"><span>03</span> Investigation</a>
          <a className="navItem" href="#safety"><span>04</span> Safety</a>
          <button className={`navItem ${view === "catalog" ? "active" : ""}`} onClick={() => setView("catalog")}><span>05</span> Catalog</button>
        </nav>
        <div className="sidebarFoot">
          <div className="environment"><span className="pulse" /> Portfolio sandbox</div>
          <p>Synthetic operations data.<br />No production mutations.</p>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar" id="overview">
          <div>
            <p className="eyebrow">INCIDENT OPERATIONS / CENTRAL INDIA</p>
            <h1>Response control center</h1>
          </div>
          <div className="systemState">
            <span className={`connectionDot ${listState}`} />
            <span>{listState === "ready" ? "Control plane connected" : listState === "error" ? "Control plane unavailable" : "Connecting"}</span>
            {listState === "error" && <button onClick={() => void loadRuns()}>Retry</button>}
          </div>
        </header>

        {view === "catalog" ? <CatalogWorkspace /> : <><section className="mission">
          <div>
            <span className="kicker">SAFETY-FIRST INCIDENT RESPONSE</span>
            <h2>Investigate quickly.<br /><em>Keep humans in control.</em></h2>
            <p>Sentinel correlates operational evidence, proposes grounded remediation, and sends every action through deterministic Java guardrails.</p>
          </div>
          <div className="modePanel">
            <span>EXECUTION MODE</span>
            <strong>DRY-RUN</strong>
            <p>Infrastructure mutation is disabled for this public environment.</p>
          </div>
        </section>

        <section className="metrics" aria-label="Incident summary">
          <Metric label="Recorded incidents" value={counts.total} note="Curated causal histories" />
          <Metric label="Escalated safely" value={counts.escalated} note="No unsafe execution" tone="warning" />
          <Metric label="Awaiting approval" value={counts.approvals} note="Human decision required" tone="blue" />
          <Metric label="Critical severity" value={counts.critical} note="SEV1 investigations" tone="danger" />
        </section>

        <section className="consoleGrid" id="incidents">
          <div className="panel incidentPanel">
            <div className="panelHead">
              <div><span className="sectionNumber">01</span><h3>Incident queue</h3></div>
              <span className="recordCount">{runs.length} RECORDS</span>
            </div>
            <p className="panelIntro">Select a scenario to inspect the real persisted investigation and safety decision.</p>
            <div className="incidentList" aria-live="polite">
              {listState === "loading" && <LoadingRows />}
              {listState === "error" && <EmptyState title="Could not load incidents" body="The backend may still be starting. Retry the connection above." />}
              {listState === "ready" && runs.map((run) => (
                <button
                  className={`incidentRow ${selectedId === run.publicId ? "selected" : ""}`}
                  key={run.publicId}
                  onClick={() => setSelectedId(run.publicId)}
                  aria-pressed={selectedId === run.publicId}
                >
                  <span className={`severity ${run.severity.toLowerCase()}`}>{run.severity}</span>
                  <span className="incidentCopy">
                    <strong>{run.scenarioTitle}</strong>
                    <small>{run.summary}</small>
                    <span className="incidentMeta"><code>{run.service}</code><time>{formatTime(run.startedAt)}</time></span>
                  </span>
                  <StatusBadge value={run.incidentStatus} />
                </button>
              ))}
            </div>
          </div>

          <div className="panel investigation" id="investigation">
            {detailState === "loading" && <InvestigationSkeleton />}
            {detailState === "error" && <EmptyState title="Investigation unavailable" body="The selected record could not be read safely." />}
            {detailState === "ready" && selected && <Investigation run={selected} />}
          </div>
        </section>

        <section className="safetyStrip" id="safety">
          <div><span className="sectionNumber">03</span><h3>Deterministic safety boundary</h3></div>
          <div className="safetyRules">
            <SafetyRule label="Kill switch" value="Ready" />
            <SafetyRule label="Mutation mode" value="Dry-run" />
            <SafetyRule label="Model authority" value="Propose only" />
            <SafetyRule label="Audit history" value="Append-only" />
          </div>
        </section>

        </>}
        <footer>
          <span>SENTINEL / SAFE AUTONOMOUS RESPONSE</span>
          <span>Spring Boot · PostgreSQL · RabbitMQ · Redis · pgvector</span>
        </footer>
      </section>
    </main>
  );
}

function Metric({ label, value, note, tone = "default" }: { label: string; value: number; note: string; tone?: string }) {
  return <article className={`metric ${tone}`}><span>{label}</span><strong>{String(value).padStart(2, "0")}</strong><small>{note}</small></article>;
}

function Investigation({ run }: { run: DemoRun }) {
  const remediation = run.remediation;
  return (
    <>
      <div className="panelHead investigationHead">
        <div><span className="sectionNumber">02</span><h3>Investigation record</h3></div>
        <span className="sourceBadge">{run.source}</span>
      </div>
      <div className="incidentTitle">
        <div><span className={`severity ${run.severity.toLowerCase()}`}>{run.severity}</span><code>{run.service}</code></div>
        <h2>{run.scenarioTitle}</h2>
        <p>{run.disclaimer}</p>
      </div>

      <div className="timeline">
        {run.timeline.map((entry) => (
          <article className="timelineEntry" key={entry.sequence}>
            <span className={`timelineMarker ${entry.type.toLowerCase()}`}>{String(entry.sequence).padStart(2, "0")}</span>
            <div>
              <header><strong>{friendly(entry.type)}</strong><time>{formatClock(entry.recordedAt)}</time></header>
              <p>{entry.content}</p>
              {entry.iteration > 0 && <small>Evaluation iteration {entry.iteration}</small>}
            </div>
          </article>
        ))}
      </div>

      {remediation ? (
        <section className="decisionCard">
          <div className="decisionTop">
            <div><span>PROPOSED ACTION</span><strong>{friendly(remediation.action)}</strong></div>
            <div className="riskDial" style={{ "--risk": `${remediation.riskScore ?? 0}%` } as React.CSSProperties}>
              <strong>{remediation.riskScore ?? "—"}</strong><span>RISK</span>
            </div>
          </div>
          <div className="grounding"><span>Grounding confidence</span><div><i style={{ width: `${Math.round(remediation.groundingSimilarity * 100)}%` }} /></div><strong>{Math.round(remediation.groundingSimilarity * 100)}%</strong></div>
          <h4>{remediation.runbook}</h4>
          <ol>{remediation.steps.map((step) => <li key={step}>{step}</li>)}</ol>
          <div className="gateResult"><span>GATE DECISION</span><StatusBadge value={remediation.status} /><p>{remediation.decisionNote}</p></div>
        </section>
      ) : (
        <section className="decisionCard noProposal"><span>NO GROUNDED REMEDIATION</span><h4>Escalated to a human operator</h4><p>Missing authoritative evidence is a hard safety stop.</p></section>
      )}

      <section className="ledger">
        <h4>Append-only action ledger <span>{run.ledger.length} EVENTS</span></h4>
        {run.ledger.length === 0 ? <p className="emptyLedger">No infrastructure action was proposed or claimed.</p> : run.ledger.map((entry, index) => (
          <div className="ledgerRow" key={`${entry.recordedAt}-${index}`}>
            <time>{formatClock(entry.recordedAt)}</time><strong>{friendly(entry.eventType)}</strong><span>{entry.actor}</span><code>{entry.mode}</code>
          </div>
        ))}
      </section>
    </>
  );
}

function StatusBadge({ value }: { value: string }) {
  return <span className={`statusBadge ${value.toLowerCase()}`}>{friendly(value)}</span>;
}

function SafetyRule({ label, value }: { label: string; value: string }) {
  return <div><span className="check">✓</span><p><small>{label}</small><strong>{value}</strong></p></div>;
}

function LoadingRows() {
  return <>{[1, 2, 3].map((row) => <div className="loadingRow" key={row}><span /><span /><span /></div>)}</>;
}

function InvestigationSkeleton() {
  return <div className="investigationSkeleton"><div /><div /><div /><div /></div>;
}

function EmptyState({ title, body }: { title: string; body: string }) {
  return <div className="emptyState"><strong>{title}</strong><p>{body}</p></div>;
}

function friendly(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (character) => character.toUpperCase());
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("en", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "UTC" }).format(new Date(value)) + " UTC";
}

function formatClock(value: string) {
  return new Intl.DateTimeFormat("en", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false, timeZone: "UTC" }).format(new Date(value));
}
