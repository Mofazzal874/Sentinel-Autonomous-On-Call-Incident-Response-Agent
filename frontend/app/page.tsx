"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import {
  Activity, ArrowRight, BookOpen, Bot, CheckCircle2, Database, ExternalLink,
  FileCheck2, FlaskConical, Gauge, GitBranch, Menu, Search, ShieldCheck,
  Siren, Sparkles, Users, X, Zap,
} from "lucide-react";
import {
  DemoRun, DemoRunSummary, DemoSystemOverview, getDemoOverview, getDemoRun, listDemoRuns,
} from "../lib/demo-api";
import CatalogWorkspace from "./CatalogWorkspace";
import EvidenceConsole from "./EvidenceConsole";
import LearningCenter from "./LearningCenter";
import ScenarioLauncher from "./ScenarioLauncher";
import ThemeControl from "./ThemeControl";
import "./experience.css";

type LoadState = "loading" | "ready" | "error";
type View = "overview" | "lab" | "incidents" | "learn" | "admin";
type DetailTab = "story" | "evidence" | "console" | "safety" | "audit";

const pipeline = [
  ["Ingest", "Accept and fingerprint the alert", Siren],
  ["Correlate", "Join deploys, metrics, logs and runbooks", GitBranch],
  ["Propose", "AI suggests; it never authorizes", Bot],
  ["Guard", "Java policy approves, blocks or escalates", ShieldCheck],
  ["Record", "Append the outcome to an immutable ledger", FileCheck2],
] as const;

export default function OperatorConsole() {
  const [view, setView] = useState<View>("overview");
  const [mobileOpen, setMobileOpen] = useState(false);
  const [runs, setRuns] = useState<DemoRunSummary[]>([]);
  const [overview, setOverview] = useState<DemoSystemOverview | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selected, setSelected] = useState<DemoRun | null>(null);
  const [listState, setListState] = useState<LoadState>("loading");
  const [detailState, setDetailState] = useState<LoadState>("loading");

  const loadData = useCallback(async (signal?: AbortSignal) => {
    setListState("loading");
    try {
      const [runResult, overviewResult] = await Promise.all([listDemoRuns(signal), getDemoOverview(signal)]);
      setRuns(runResult);
      setOverview(overviewResult);
      setSelectedId((current) => current ?? runResult[0]?.publicId ?? null);
      setListState("ready");
    } catch (error) {
      if ((error as Error).name !== "AbortError") setListState("error");
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void loadData(controller.signal);
    return () => controller.abort();
  }, [loadData]);

  useEffect(() => {
    if (!selectedId) return;
    const controller = new AbortController();
    setDetailState("loading");
    getDemoRun(selectedId, controller.signal).then((result) => {
      setSelected(result); setDetailState("ready");
    }).catch((error: Error) => { if (error.name !== "AbortError") setDetailState("error"); });
    return () => controller.abort();
  }, [selectedId]);

  const navigate = (next: View) => { setView(next); setMobileOpen(false); window.scrollTo({ top: 0, behavior: "smooth" }); };
  const openCompletedRun = useCallback(async (publicId: string) => {
    await loadData(); setSelectedId(publicId); setView("incidents");
  }, [loadData]);

  return <main className="experienceShell">
    <header className="experienceNav">
      <button className="wordmark" onClick={() => navigate("overview")} aria-label="Sentinel home"><span><ShieldCheck /></span>Sentinel</button>
      <button className="mobileMenu" onClick={() => setMobileOpen(!mobileOpen)} aria-label="Toggle navigation">{mobileOpen ? <X /> : <Menu />}</button>
      <nav className={mobileOpen ? "open" : ""} aria-label="Primary navigation">
        <NavButton active={view === "overview"} onClick={() => navigate("overview")}>Overview</NavButton>
        <NavButton active={view === "lab"} onClick={() => navigate("lab")}><FlaskConical /> Live lab</NavButton>
        <NavButton active={view === "incidents"} onClick={() => navigate("incidents")}>Incidents <span>{runs.length}</span></NavButton>
        <NavButton active={view === "learn"} onClick={() => navigate("learn")}><BookOpen /> Learn</NavButton>
        <NavButton active={view === "admin"} onClick={() => navigate("admin")}>Admin</NavButton>
      </nav>
      <div className="navUtilities"><div className={`apiState ${listState}`}><i />{listState === "ready" ? "System live" : listState === "error" ? "API unavailable" : "Connecting"}</div><ThemeControl /></div>
    </header>

    <AnimatePresence mode="wait">
      <motion.div key={view} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: .28 }}>
        {view === "overview" && <Overview overview={overview} runs={runs} onLab={() => navigate("lab")} onLearn={() => navigate("learn")} />}
        {view === "lab" && <LiveLab onCompleted={openCompletedRun} />}
        {view === "incidents" && <IncidentExplorer runs={runs} selectedId={selectedId} selected={selected} listState={listState} detailState={detailState} onSelect={setSelectedId} onRetry={() => void loadData()} />}
        {view === "learn" && <LearningCenter onLab={() => navigate("lab")} onIncidents={() => navigate("incidents")} />}
        {view === "admin" && <div className="legacyWrap"><CatalogWorkspace /></div>}
      </motion.div>
    </AnimatePresence>
    <footer className="experienceFooter"><div><strong>Sentinel</strong><span>Safe autonomous incident response</span></div><p>Spring Boot · PostgreSQL · RabbitMQ · Redis · pgvector</p><a href="https://github.com/Mofazzal874/Sentinel-Autonomous-On-Call-Incident-Response-Agent" target="_blank" rel="noreferrer">Source code <ExternalLink /></a></footer>
  </main>;
}

function Overview({ overview, runs, onLab, onLearn }: { overview: DemoSystemOverview | null; runs: DemoRunSummary[]; onLab: () => void; onLearn: () => void }) {
  return <>
    <section className="heroSection">
      <div className="heroGlow" />
      <motion.div className="heroCopy" initial="hidden" animate="show" variants={{ show: { transition: { staggerChildren: .09 } } }}>
        <motion.p variants={rise} className="heroLabel"><Sparkles /> A working incident-response sandbox</motion.p>
        <motion.h1 variants={rise}>Turn alert noise into a <em>safe, explainable response.</em></motion.h1>
        <motion.p variants={rise} className="heroLead">On-call engineers lose time joining scattered evidence—and an AI-generated fix can make an outage worse. Sentinel assembles the evidence, proposes one bounded action, and lets deterministic policy keep the human in control.</motion.p>
        <motion.div variants={rise} className="heroActions"><button className="primaryCta" onClick={onLab}>Launch a live incident <ArrowRight /></button><button className="secondaryCta" onClick={onLearn}><BookOpen /> How it works</button></motion.div>
        <motion.div variants={rise} className="truthLine"><span><CheckCircle2 /> Real database records</span><span><CheckCircle2 /> Real queue processing</span><span><ShieldCheck /> Zero production mutations</span></motion.div>
      </motion.div>
      <motion.div className="heroVisual" initial={{ opacity: 0, scale: .96 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: .25 }}>
        <div className="visualTop"><span><i /> INCIDENT IN PROGRESS</span><code>payments-api / SEV1</code></div>
        <div className="signal"><Activity /><div><small>Observed signal</small><strong>Error rate jumped after release</strong><span>Logs + metrics + deploy history correlated</span></div></div>
        <div className="decisionFlow"><div><Bot /><span>AI proposal</span><strong>Rollback deployment</strong></div><ArrowRight /><div className="guard"><ShieldCheck /><span>Java guardrail</span><strong>DRY-RUN ONLY</strong></div></div>
        <div className="visualFoot"><span>✓ Evidence grounded</span><span>✓ Audit event written</span></div>
      </motion.div>
    </section>

    <section className="proofBand" aria-label="Live database snapshot">
      <div><p>LIVE DATABASE SNAPSHOT</p><h2>This is not a static mockup.</h2><span>These totals are queried from PostgreSQL when this page loads.</span></div>
      <Proof value={overview?.services} label="services" icon={Database} />
      <Proof value={overview?.metricSamples} label="metric samples" icon={Gauge} />
      <Proof value={overview?.logEvents} label="log events" icon={Activity} />
      <Proof value={overview?.ledgerEvents} label="audit events" icon={FileCheck2} />
    </section>

    <section className="storySection">
      <div className="sectionHeading"><p>THE PROBLEM, MADE CONCRETE</p><h2>One alert. Five accountable decisions.</h2><span>Follow the same path a real on-call engineer would inspect during an outage.</span></div>
      <div className="pipelineCards">{pipeline.map(([title, body, Icon], index) => <motion.article key={title} whileHover={{ y: -6 }}><span className="stepIndex">0{index + 1}</span><Icon /><h3>{title}</h3><p>{body}</p>{index < pipeline.length - 1 && <ArrowRight className="flowArrow" />}</motion.article>)}</div>
    </section>

    <section className="personaSection"><div><p>WHO USES IT?</p><h2>Built for the moment when every minute matters.</h2></div><div className="personaGrid"><Persona icon={Siren} title="On-call engineer" text="Gets one evidence-backed incident instead of hunting across four tools." /><Persona icon={Users} title="SRE approver" text="Sees the proposed action, risk, grounding and audit history before deciding." /><Persona icon={ShieldCheck} title="Platform team" text="Defines the deterministic policy boundary that neither a user nor a model can bypass." /></div></section>
    <section className="finalCta"><div><span>READY TO SEE THE BACKEND MOVE?</span><h2>Run a safe incident yourself.</h2><p>Choose one server-owned scenario. Sentinel will create fresh records, publish durable work, investigate it, apply the safety gate and return the persisted result.</p></div><button className="primaryCta" onClick={onLab}>Open the live lab <Zap /></button><small>{runs.filter(run => run.source === "LIVE").length} visitor-triggered runs recorded so far</small></section>
  </>;
}

function LiveLab({ onCompleted }: { onCompleted: (id: string) => Promise<void> }) {
  return <section className="pageSection"><div className="pageIntro"><p>INTERACTIVE DEMO</p><h1>Cause a safe failure.<br /><em>Watch Sentinel reason.</em></h1><span>This is a real backend workflow—not a frontend animation. It creates fresh telemetry and an incident, sends work through RabbitMQ, persists the investigation, and records the guardrail outcome.</span></div><ScenarioLauncher onCompleted={onCompleted} /></section>;
}

function IncidentExplorer({ runs, selectedId, selected, listState, detailState, onSelect, onRetry }: { runs: DemoRunSummary[]; selectedId: string | null; selected: DemoRun | null; listState: LoadState; detailState: LoadState; onSelect: (id: string) => void; onRetry: () => void }) {
  const [query, setQuery] = useState(""); const [severity, setSeverity] = useState("ALL"); const [tab, setTab] = useState<DetailTab>("story");
  const filtered = useMemo(() => runs.filter(run => (severity === "ALL" || run.severity === severity) && `${run.scenarioTitle} ${run.service} ${run.summary}`.toLowerCase().includes(query.toLowerCase())), [runs, query, severity]);
  return <section className="pageSection incidentPage"><div className="pageIntro compact"><p>PERSISTED INVESTIGATIONS</p><h1>Incident explorer</h1><span>Every row below comes from PostgreSQL. Select one to inspect the evidence, agent proposal, deterministic safety decision and append-only audit trail.</span></div>
    <div className="explorerLayout"><aside className="queuePanel"><div className="filterBar"><label><Search /><input aria-label="Search incidents" value={query} onChange={e => setQuery(e.target.value)} placeholder="Search incidents or services" /></label><select aria-label="Filter by severity" value={severity} onChange={e => setSeverity(e.target.value)}><option value="ALL">All severity</option><option>SEV1</option><option>SEV2</option><option>SEV3</option><option>SEV4</option></select></div>
      <p className="resultCount">{filtered.length} of {runs.length} records</p>{listState === "error" && <div className="largeEmpty"><strong>Could not reach the API</strong><button onClick={onRetry}>Retry</button></div>}{listState === "loading" && <LoadingRows />}
      <div className="runList">{filtered.map(run => <button key={run.publicId} className={selectedId === run.publicId ? "active" : ""} onClick={() => onSelect(run.publicId)}><div><Severity value={run.severity} /><span className={`source ${run.source.toLowerCase()}`}>{run.source}</span></div><strong>{run.scenarioTitle}</strong><p>{run.summary}</p><footer><code>{run.service}</code><Status value={run.incidentStatus} /></footer></button>)}</div></aside>
      <article className="detailPanel">{detailState === "loading" && <InvestigationSkeleton />}{detailState === "error" && <div className="largeEmpty"><strong>Investigation unavailable</strong><p>The selected record could not be read.</p></div>}{detailState === "ready" && selected && <Investigation run={selected} tab={tab} setTab={setTab} />}</article></div>
  </section>;
}

function Investigation({ run, tab, setTab }: { run: DemoRun; tab: DetailTab; setTab: (tab: DetailTab) => void }) {
  const remediation = run.remediation;
  return <><header className="detailHeader"><div><div className="detailMeta"><Severity value={run.severity} /><span className={`source ${run.source.toLowerCase()}`}>{run.source}</span><code>{run.service}</code></div><h2>{run.scenarioTitle}</h2><p>{run.summary}</p></div><Status value={run.incidentStatus} /></header>
    <nav className="detailTabs" aria-label="Investigation sections">{([['story','Response brief'],['evidence','Evidence & AI'],['console','Raw console'],['safety','Safety decision'],['audit','Audit ledger']] as [DetailTab,string][]).map(([id,label]) => <button className={tab === id ? "active" : ""} onClick={() => setTab(id)} key={id}>{label}{id === "audit" && <span>{run.ledger.length}</span>}</button>)}</nav>
    <AnimatePresence mode="wait"><motion.div className="detailBody" key={tab} initial={{ opacity: 0, x: 8 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -8 }}>
      {tab === "story" && <><div className="responseBrief"><article><span>CUSTOMER IMPACT</span><strong>{run.summary}</strong></article><article><span>LIKELY INCIDENT TYPE</span><strong>{run.timeline.find(entry => entry.type === "CLASSIFICATION")?.content ?? "Escalated for operator classification"}</strong></article><article><span>RECOMMENDED NEXT MOVE</span><strong>{remediation ? `${friendly(remediation.action)} · ${friendly(remediation.status)}` : "Escalate; no grounded action"}</strong></article></div><h3 className="sectionLabel">How Sentinel reached this result</h3><div className="readableTimeline">{run.timeline.map(entry => <article key={entry.sequence}><span>{entry.sequence}</span><div><header><strong>{friendly(entry.type)}</strong><time>{formatClock(entry.recordedAt)}</time></header><p>{entry.content}</p></div></article>)}</div></>}
      {tab === "evidence" && <><Explainer title="What did the agent do?" text="It classified the incident, gathered bounded evidence, retrieved a runbook, proposed an action and critiqued that proposal. It did not authorize execution." /><div className="evidenceGrid">{run.timeline.filter(entry => entry.type !== "OUTCOME").map(entry => <article key={entry.sequence}><span>{friendly(entry.type)}</span><p>{entry.content}</p>{entry.iteration > 0 && <small>Evaluator pass {entry.iteration}</small>}</article>)}</div>{remediation && <div className="reasonGrid"><article><span>Why this action?</span><p>{remediation.rationale}</p></article><article><span>Known risk</span><p>{remediation.riskNotes}</p></article></div>}</>}
      {tab === "console" && <EvidenceConsole run={run} />}
      {tab === "safety" && (remediation ? <><Explainer title="Who made the final decision?" text="Deterministic Java code—not the AI model—scored risk and applied kill-switch, allowlist, grounding, approval and idempotency rules." /><div className="decisionHero"><div><small>PROPOSED ACTION</small><h3>{friendly(remediation.action)}</h3><p>{remediation.runbook}</p></div><div className="score"><strong>{remediation.riskScore ?? "—"}</strong><span>risk / 100</span></div><div className="score safe"><strong>{Math.round(remediation.groundingSimilarity * 100)}%</strong><span>grounded</span></div></div><ol className="actionSteps">{remediation.steps.map((step,index) => <li key={step}><span>{index + 1}</span>{step}</li>)}</ol><div className="gateBanner"><ShieldCheck /><div><small>DETERMINISTIC GATE RESULT</small><strong>{friendly(remediation.status)}</strong><p>{remediation.decisionNote ?? "The proposal remained inside the configured safety boundary."}</p></div></div></> : <div className="noRemediation"><ShieldCheck /><h3>No grounded remediation was allowed</h3><p>Sentinel escalated instead of inventing a fix. Missing authoritative evidence is a hard safety stop.</p></div>)}
      {tab === "audit" && <><Explainer title="Why an append-only ledger?" text="An action is never erased or rewritten. Later compensation becomes a new fact, preserving who decided what and when." /><div className="auditTable">{run.ledger.length ? run.ledger.map((entry,index) => <article key={`${entry.recordedAt}-${index}`}><time>{formatClock(entry.recordedAt)}</time><span className="auditDot" /><div><strong>{friendly(entry.eventType)}</strong><p>{entry.details}</p><small>{entry.actor} · {friendly(entry.mode)}</small></div></article>) : <div className="largeEmpty"><strong>No mutation was claimed</strong><p>The safe outcome itself is visible in the investigation timeline.</p></div>}</div></>}
    </motion.div></AnimatePresence></>;
}

const rise = { hidden: { opacity: 0, y: 18 }, show: { opacity: 1, y: 0 } };
function NavButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) { return <button className={active ? "active" : ""} onClick={onClick}>{children}</button>; }
function Proof({ value, label, icon: Icon }: { value?: number; label: string; icon: typeof Database }) { return <div className="proof"><Icon /><strong>{value === undefined ? "—" : value.toLocaleString()}</strong><span>{label}</span></div>; }
function Persona({ icon: Icon, title, text }: { icon: typeof Siren; title: string; text: string }) { return <article><Icon /><h3>{title}</h3><p>{text}</p></article>; }
function Severity({ value }: { value: string }) { return <span className={`newSeverity ${value.toLowerCase()}`}>{value}</span>; }
function Status({ value }: { value: string }) { return <span className={`newStatus ${value.toLowerCase()}`}>{friendly(value)}</span>; }
function Explainer({ title, text }: { title: string; text: string }) { return <div className="explainer"><BookOpen /><div><strong>{title}</strong><p>{text}</p></div></div>; }
function LoadingRows() { return <div className="newLoading">{[1,2,3].map(row => <div key={row}><i /><span /><span /></div>)}</div>; }
function InvestigationSkeleton() { return <div className="detailSkeleton"><i /><i /><i /><i /></div>; }
function friendly(value: string) { return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, c => c.toUpperCase()); }
function formatClock(value: string) { return new Intl.DateTimeFormat("en", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false, timeZone: "UTC" }).format(new Date(value)) + " UTC"; }
