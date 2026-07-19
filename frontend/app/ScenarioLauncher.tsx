"use client";

import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Activity, ArrowRight, Bot, CheckCircle2, Clock3, Database, FileCheck2, GitBranch, LoaderCircle, Play, ShieldCheck, Siren } from "lucide-react";
import { DemoScenario, DemoSubmission, getDemoSubmission, listDemoScenarios, submitDemoScenario } from "../lib/demo-api";

const stages = [
  ["Alert accepted", Siren], ["Evidence created", Database], ["Queued", GitBranch],
  ["Agent evaluating", Bot], ["Guardrail applied", ShieldCheck], ["Persisted", FileCheck2],
] as const;

export default function ScenarioLauncher({ onCompleted }: { onCompleted: (publicId: string) => Promise<void> }) {
  const [scenarios, setScenarios] = useState<DemoScenario[]>([]);
  const [selected, setSelected] = useState("");
  const [submission, setSubmission] = useState<DemoSubmission | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listDemoScenarios(controller.signal).then(items => { setScenarios(items); setSelected(items[0]?.id ?? ""); })
      .catch((failure: Error) => { if (failure.name !== "AbortError") setError("Live scenarios are temporarily unavailable."); });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!busy) return;
    const timer = window.setInterval(() => setStage(current => Math.min(current + 1, 4)), 1800);
    return () => window.clearInterval(timer);
  }, [busy]);

  const active = useMemo(() => scenarios.find(scenario => scenario.id === selected), [scenarios, selected]);

  async function run() {
    if (!selected || busy) return;
    setBusy(true); setStage(0); setSubmission(null); setError(null);
    try {
      let current = await submitDemoScenario(selected, crypto.randomUUID());
      setSubmission(current); setStage(2);
      for (let attempt = 0; attempt < 90 && !["COMPLETED", "FAILED"].includes(current.state); attempt++) {
        await delay(2000); current = await getDemoSubmission(current.publicId); setSubmission(current);
      }
      if (current.state === "COMPLETED") { setStage(5); await delay(450); await onCompleted(current.publicId); }
      else if (current.state === "FAILED") setError(current.failureReason ?? "The bounded investigation failed safely.");
      else setError("The investigation is still running. Its durable ID remains available in the incident queue.");
    } catch (failure) { setError((failure as Error).message); }
    finally { setBusy(false); }
  }

  return <div className="labWorkspace">
    <section className="scenarioChooser"><header><span>STEP 1</span><div><h2>Choose a failure to investigate</h2><p>The options come from the backend catalog. Arbitrary prompts and infrastructure commands are intentionally rejected.</p></div></header>
      <div className="scenarioCards" aria-label="Failure scenarios">{scenarios.map((scenario,index) => <motion.button key={scenario.id} className={selected === scenario.id ? "active" : ""} onClick={() => !busy && setSelected(scenario.id)} whileHover={{ y: -4 }} disabled={busy}>
        <div><span className={`newSeverity ${scenario.severity.toLowerCase()}`}>{scenario.severity}</span><small>0{index + 1}</small></div><Siren /><h3>{scenario.displayName}</h3><code>{scenario.service}</code><p>{scenario.description}</p><span className="selectMark">{selected === scenario.id ? <CheckCircle2 /> : <span />}</span>
      </motion.button>)}</div>
    </section>

    <section className="launchPanel"><div><span>STEP 2</span><h2>Send it through the real system</h2><p>{active ? <><strong>{active.displayName}</strong> will create a fresh {active.severity} incident for <code>{active.service}</code>.</> : "Loading the scenario catalog…"}</p></div><button className="launchButton" onClick={() => void run()} disabled={busy || !selected}>{busy ? <><LoaderCircle className="spin" /> Investigation running</> : <><Play /> Run this incident</>}</button></section>

    <section className="executionPanel"><header><span>STEP 3</span><div><h2>Watch the backend process it</h2><p>Progress is polled from the durable submission record. The animation only visualizes state reported by the API.</p></div>{submission && <code>{submission.publicId}</code>}</header>
      <div className="stageTrack">{stages.map(([label,Icon],index) => <div className={stage > index || (!busy && submission?.state === "COMPLETED") ? "done" : stage === index && busy ? "current" : ""} key={label}><span><Icon /></span><strong>{label}</strong>{index < stages.length - 1 && <i><b /></i>}</div>)}</div>
      <AnimatePresence mode="wait"><motion.div key={error ?? submission?.state ?? "idle"} className={`liveMessage ${error ? "error" : ""}`} initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
        {error ? <><ShieldCheck /><div><strong>Stopped safely</strong><p>{error}</p></div></> : submission ? <><Activity /><div><strong>{friendly(submission.state)}</strong><p>{statusMessage(submission)}</p></div></> : <><Clock3 /><div><strong>Ready when you are</strong><p>Typical completion time is 5–15 seconds. Public limits: two concurrent runs and 25 runs per day.</p></div></>}
      </motion.div></AnimatePresence>
    </section>
  </div>;
}

function statusMessage(submission: DemoSubmission) {
  if (submission.state === "COMPLETED") return "Investigation complete. Opening the persisted incident record now.";
  if (submission.state === "FAILED") return "The workflow failed without an infrastructure mutation.";
  if (submission.state === "QUEUED") return "RabbitMQ accepted the incident. A consumer will acknowledge it only after durable processing.";
  return "The request has a durable identifier and is entering the bounded workflow.";
}
function friendly(value: string) { return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, c => c.toUpperCase()); }
function delay(milliseconds: number) { return new Promise(resolve => setTimeout(resolve, milliseconds)); }
