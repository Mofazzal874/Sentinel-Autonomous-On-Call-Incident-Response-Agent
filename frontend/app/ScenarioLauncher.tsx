"use client";

import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Activity, ArrowRight, Bot, CheckCircle2, Clock3, Database, FileCheck2, GitBranch, LoaderCircle, Play, ShieldCheck, Siren, Terminal } from "lucide-react";
import { DemoChoice, DemoInvestigationOptions, DemoInvestigationRequest, DemoSubmission, getDemoInvestigationOptions, getDemoSubmission, submitDemoInvestigation } from "../lib/demo-api";

const stages = [["Request accepted", Siren], ["Evidence persisted", Database], ["Work queued", GitBranch], ["Agent investigates", Bot], ["Policy evaluated", ShieldCheck], ["Record complete", FileCheck2]] as const;

export default function ScenarioLauncher({ onCompleted }: { onCompleted: (publicId: string) => Promise<void> }) {
  const [options, setOptions] = useState<DemoInvestigationOptions | null>(null);
  const [form, setForm] = useState<DemoInvestigationRequest>({ serviceId: "", symptom: "BAD_DEPLOY", severity: "SEV2", signalIntensity: "HIGH", customerImpact: "PARTIAL_OUTAGE", deploymentContext: "RECENT_CHANGE" });
  const [submission, setSubmission] = useState<DemoSubmission | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState(0);
  const [consoleLines, setConsoleLines] = useState<string[]>(["$ sentinel investigate --configure", "Waiting for an operator-defined incident…"]);

  useEffect(() => {
    const controller = new AbortController();
    getDemoInvestigationOptions(controller.signal).then(result => {
      setOptions(result); setForm(current => ({ ...current, serviceId: result.services[0]?.id ?? "" }));
    }).catch((failure: Error) => { if (failure.name !== "AbortError") setError("The investigation catalog is temporarily unavailable."); });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!busy) return;
    const timer = window.setInterval(() => setStage(current => Math.min(current + 1, 4)), 1800);
    return () => window.clearInterval(timer);
  }, [busy]);

  const service = useMemo(() => options?.services.find(item => item.id === form.serviceId), [options, form.serviceId]);
  const symptom = options?.symptoms.find(item => item.value === form.symptom);

  async function run() {
    if (!form.serviceId || busy) return;
    setBusy(true); setStage(0); setSubmission(null); setError(null);
    setConsoleLines([`$ sentinel investigate --service ${service?.name} --symptom ${form.symptom.toLowerCase()}`, `[request] severity=${form.severity} intensity=${form.signalIntensity} impact=${form.customerImpact}`, "[api] POST /api/v1/demo/investigations"]);
    try {
      let current = await submitDemoInvestigation(form, crypto.randomUUID());
      setSubmission(current); setStage(2); setConsoleLines(lines => [...lines, `[accepted] public_id=${current.publicId}`, "[queue] durable submission is QUEUED; awaiting consumer acknowledgement"]);
      let lastState = current.state;
      for (let attempt = 0; attempt < 420 && !["COMPLETED", "FAILED"].includes(current.state); attempt++) {
        await delay(2000); current = await getDemoSubmission(current.publicId); setSubmission(current);
        if (current.state !== lastState) { setConsoleLines(lines => [...lines, `[state] ${lastState} -> ${current.state}`]); lastState = current.state; }
        if (attempt > 0 && attempt % 15 === 0) setConsoleLines(lines => [...lines, `[agent] CPU-hosted model is still evaluating · elapsed=${Math.round((attempt * 2) / 60)}m · durable_id=${current.publicId}`]);
      }
      if (current.state === "COMPLETED") {
        setStage(5); setConsoleLines(lines => [...lines, `[database] incident_status=${current.incidentStatus}`, "[guardrail] execution_mode=DRY_RUN", "[done] Open the persisted report to inspect every evidence row."]);
      } else if (current.state === "FAILED") setError(current.failureReason ?? "The bounded investigation failed safely.");
      else setError("The investigation exceeded the public 14-minute observation window. Its durable ID remains available for later inspection.");
    } catch (failure) { setError((failure as Error).message); }
    finally { setBusy(false); }
  }

  return <div className="labWorkspace">
    <section className="scenarioChooser"><header><span>STEP 1</span><div><h2>Describe the incident you want to test</h2><p>Choose from the seeded service catalog and bounded failure vocabulary. Each combination creates new, queryable records—not browser-only variables.</p></div></header>
      <div className="builderGrid">
        <Field label="Affected service"><select aria-label="Affected service" value={form.serviceId} onChange={event => setForm({ ...form, serviceId: event.target.value })}>{options?.services.map(item => <option key={item.id} value={item.id}>{item.name} · {item.team} · {item.tier}</option>)}</select><small>{service ? `Allowed actions: ${service.allowedActions.join(", ") || "none"}` : "Loading services…"}</small></Field>
        <Field label="Observed failure"><select aria-label="Observed failure" value={form.symptom} onChange={event => setForm({ ...form, symptom: event.target.value })}>{options?.symptoms.map(choice => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select><small>{symptom?.description}</small></Field>
        <ChoiceGroup label="Severity" choices={options?.severities ?? []} value={form.severity} onChange={value => setForm({ ...form, severity: value })} />
        <ChoiceGroup label="Signal strength" choices={options?.signalIntensities ?? []} value={form.signalIntensity} onChange={value => setForm({ ...form, signalIntensity: value })} />
        <Field label="Customer impact"><select aria-label="Customer impact" value={form.customerImpact} onChange={event => setForm({ ...form, customerImpact: event.target.value })}>{options?.customerImpacts.map(choice => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select><small>{options?.customerImpacts.find(item => item.value === form.customerImpact)?.description}</small></Field>
        <Field label="Change context"><select aria-label="Change context" value={form.deploymentContext} onChange={event => setForm({ ...form, deploymentContext: event.target.value })}>{options?.deploymentContexts.map(choice => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select><small>{options?.deploymentContexts.find(item => item.value === form.deploymentContext)?.description}</small></Field>
      </div>
    </section>

    <section className="launchPanel"><div><span>STEP 2</span><h2>Review the evidence contract</h2><p>{options ? <>Sentinel will persist <strong>{options.evidencePlan.metricSeries * options.evidencePlan.samplesPerSeries} metric samples</strong>, <strong>{options.evidencePlan.logEvents} log events</strong>{form.deploymentContext === "RECENT_CHANGE" ? ", and one deployment" : " with no deployment"}. The outcome remains <code>{options.evidencePlan.executionMode}</code>.</> : "Loading the server-owned evidence plan…"}</p></div><button className="launchButton" onClick={() => void run()} disabled={busy || !form.serviceId}>{busy ? <><LoaderCircle className="spin" /> Investigating</> : <><Play /> Create investigation</>}</button></section>

    <section className="executionPanel"><header><span>STEP 3</span><div><h2>Watch the real workflow</h2><p>The status and identifier come from PostgreSQL. The console narrates API state; the completed report exposes the raw database evidence.</p></div>{submission && <code>{submission.publicId}</code>}</header>
      <div className="stageTrack">{stages.map(([label,Icon],index) => <div className={stage > index || (!busy && submission?.state === "COMPLETED") ? "done" : stage === index && busy ? "current" : ""} key={label}><span><Icon /></span><strong>{label}</strong>{index < stages.length - 1 && <i><b /></i>}</div>)}</div>
      <div className="liveConsole"><header><span><Terminal /> SENTINEL EVENT STREAM</span><i>{busy ? "LIVE" : submission?.state ?? "READY"}</i></header><pre>{consoleLines.map((line,index) => <code key={`${line}-${index}`}>{line}</code>)}</pre></div>
      <AnimatePresence mode="wait"><motion.div key={error ?? submission?.state ?? "idle"} className={`liveMessage ${error ? "error" : ""}`} initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
        {error ? <><ShieldCheck /><div><strong>Stopped safely</strong><p>{error}</p></div></> : submission ? <><Activity /><div><strong>{friendly(submission.state)}</strong><p>{statusMessage(submission)}</p>{submission.state === "COMPLETED" && <button className="openReport" onClick={() => void onCompleted(submission.publicId)}>Open evidence-backed report <ArrowRight /></button>}</div></> : <><Clock3 /><div><strong>Ready when you are</strong><p>Public use is rate-limited. The real CPU-hosted model may take several minutes; the generated ID keeps the work durable.</p></div></>}
      </motion.div></AnimatePresence>
    </section>
  </div>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="builderField"><strong>{label}</strong>{children}</label>; }
function ChoiceGroup({ label, choices, value, onChange }: { label: string; choices: DemoChoice[]; value: string; onChange: (value: string) => void }) { return <fieldset className="choiceField"><legend>{label}</legend><div>{choices.map(choice => <button type="button" key={choice.value} className={value === choice.value ? "active" : ""} title={choice.description} onClick={() => onChange(choice.value)}>{choice.label}</button>)}</div><small>{choices.find(item => item.value === value)?.description}</small></fieldset>; }
function statusMessage(submission: DemoSubmission) { if (submission.state === "COMPLETED") return "Investigation complete. The report contains the exact persisted metrics, logs, deployment, runbook, model transcript and policy outcome."; if (submission.state === "FAILED") return "The workflow failed without an infrastructure mutation."; if (submission.state === "QUEUED") return "RabbitMQ accepted the work. The CPU-hosted model can take several minutes; the consumer acknowledges only after durable processing."; return "The request has a durable identifier and is entering the bounded workflow."; }
function friendly(value: string) { return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, character => character.toUpperCase()); }
function delay(milliseconds: number) { return new Promise(resolve => setTimeout(resolve, milliseconds)); }
