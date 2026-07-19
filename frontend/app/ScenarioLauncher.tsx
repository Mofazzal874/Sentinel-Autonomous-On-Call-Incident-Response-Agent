"use client";

import { useEffect, useState } from "react";
import {
  DemoScenario,
  DemoSubmission,
  getDemoSubmission,
  listDemoScenarios,
  submitDemoScenario,
} from "../lib/demo-api";

export default function ScenarioLauncher({ onCompleted }: { onCompleted: (publicId: string) => Promise<void> }) {
  const [scenarios, setScenarios] = useState<DemoScenario[]>([]);
  const [selected, setSelected] = useState<string>("");
  const [submission, setSubmission] = useState<DemoSubmission | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    listDemoScenarios(controller.signal).then((items) => {
      setScenarios(items);
      setSelected(items[0]?.id ?? "");
    }).catch((failure: Error) => {
      if (failure.name !== "AbortError") setError("Live scenarios are temporarily unavailable.");
    });
    return () => controller.abort();
  }, []);

  async function run() {
    if (!selected || busy) return;
    setBusy(true);
    setError(null);
    try {
      let current = await submitDemoScenario(selected, crypto.randomUUID());
      setSubmission(current);
      for (let attempt = 0; attempt < 90 && !["COMPLETED", "FAILED"].includes(current.state); attempt++) {
        await delay(2000);
        current = await getDemoSubmission(current.publicId);
        setSubmission(current);
      }
      if (current.state === "COMPLETED") {
        await onCompleted(current.publicId);
      } else if (current.state === "FAILED") {
        setError(current.failureReason ?? "The bounded investigation failed safely.");
      } else {
        setError("The investigation is still running. Its durable ID remains available in the incident queue.");
      }
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return <section className="scenarioLauncher" aria-labelledby="scenario-title">
    <div className="scenarioIntro">
      <span className="sectionNumber">LIVE</span>
      <div><h3 id="scenario-title">Run a bounded incident</h3><p>Choose one server-owned failure. Sentinel creates fresh evidence, queues durable triage, and applies the same dry-run guardrail path.</p></div>
    </div>
    <div className="scenarioControls">
      <label htmlFor="scenario-select">Failure scenario</label>
      <select id="scenario-select" value={selected} onChange={(event) => setSelected(event.target.value)} disabled={busy || scenarios.length === 0}>
        {scenarios.map((scenario) => <option value={scenario.id} key={scenario.id}>{scenario.severity} / {scenario.displayName} / {scenario.service}</option>)}
      </select>
      <button onClick={() => void run()} disabled={busy || !selected}>{busy ? "Investigating..." : "Run safe scenario"}</button>
    </div>
    <div className="scenarioStatus" aria-live="polite">
      {submission ? <><code>{submission.publicId}</code><span className={`statusBadge ${submission.state.toLowerCase()}`}>{submission.state}</span><p>{statusMessage(submission)}</p></> : <p>No arbitrary prompt or infrastructure input is accepted. Maximum two concurrent and 25 daily public runs.</p>}
      {error && <strong>{error}</strong>}
    </div>
  </section>;
}

function statusMessage(submission: DemoSubmission) {
  if (submission.state === "COMPLETED") return "Investigation complete. The persisted record is selected below.";
  if (submission.state === "FAILED") return "The workflow failed safely without an infrastructure mutation.";
  return "RabbitMQ accepted the incident. Evidence gathering and bounded agent evaluation are in progress.";
}

function delay(milliseconds: number) { return new Promise((resolve) => setTimeout(resolve, milliseconds)); }
