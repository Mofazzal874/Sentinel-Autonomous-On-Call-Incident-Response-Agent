"use client";

import { useMemo, useState } from "react";
import { Check, Clipboard, Terminal, WrapText } from "lucide-react";
import { DemoRun } from "../lib/demo-api";

type Kind = "ALL" | "QUERY" | "METRIC" | "LOG" | "AGENT" | "POLICY";
type Line = { kind: Exclude<Kind, "ALL">; time: string; text: string };

export default function EvidenceConsole({ run }: { run: DemoRun }) {
  const [filter, setFilter] = useState<Kind>("ALL");
  const [wrap, setWrap] = useState(true);
  const [copied, setCopied] = useState(false);
  const lines = useMemo(() => buildLines(run), [run]);
  const visible = filter === "ALL" ? lines : lines.filter(line => line.kind === filter);
  async function copy() { await navigator.clipboard.writeText(visible.map(line => `[${line.time}] ${line.kind.padEnd(6)} ${line.text}`).join("\n")); setCopied(true); window.setTimeout(() => setCopied(false), 1400); }
  return <div className="evidenceConsole">
    <header><div><Terminal /><span>DATABASE-BACKED INVESTIGATION STREAM</span></div><div><button onClick={() => setWrap(!wrap)} className={wrap ? "active" : ""}><WrapText /> Wrap</button><button onClick={() => void copy()}>{copied ? <Check /> : <Clipboard />} {copied ? "Copied" : "Copy"}</button></div></header>
    <nav>{(["ALL","QUERY","METRIC","LOG","AGENT","POLICY"] as Kind[]).map(kind => <button key={kind} className={filter === kind ? "active" : ""} onClick={() => setFilter(kind)}>{kind}</button>)}</nav>
    <div className={wrap ? "consoleOutput wrap" : "consoleOutput"}>{visible.map((line,index) => <div key={`${line.kind}-${index}`}><time>{line.time}</time><span className={`consoleKind ${line.kind.toLowerCase()}`}>{line.kind}</span><code>{line.text}</code></div>)}</div>
    <footer>{visible.length} lines · bounded read-only evidence · {run.disclaimer}</footer>
  </div>;
}

function buildLines(run: DemoRun): Line[] {
  const time = (value: string) => new Date(value).toISOString().slice(11, 23);
  const lines: Line[] = [{ kind: "QUERY", time: time(run.startedAt), text: `$ investigate service=${run.service} incident=${run.publicId}` }];
  lines.push({ kind: "QUERY", time: time(run.startedAt), text: `SELECT deployments WHERE service='${run.service}' WINDOW -30m..+5m LIMIT 10` });
  run.evidence.deployments.forEach(item => lines.push({ kind: "QUERY", time: time(item.deployedAt), text: `deployment version=${item.version} sha=${item.gitSha} status=${item.status} actor=${item.deployedBy}` }));
  if (!run.evidence.deployments.length) lines.push({ kind: "QUERY", time: time(run.startedAt), text: "deployment result_count=0 (no recent change)" });
  run.evidence.metrics.forEach(series => {
    const values = series.points.map(point => point.value);
    const latest = series.points.at(-1);
    lines.push({ kind: "METRIC", time: time(latest?.recordedAt ?? run.startedAt), text: `${series.metric} samples=${values.length} min=${Math.min(...values).toFixed(2)} max=${Math.max(...values).toFixed(2)} latest=${latest?.value.toFixed(2) ?? "n/a"}` });
  });
  run.evidence.logs.forEach(item => lines.push({ kind: "LOG", time: time(item.occurredAt), text: `${item.level.padEnd(5)} trace=${item.traceId} ${item.message}` }));
  run.timeline.forEach(item => lines.push({ kind: "AGENT", time: time(item.recordedAt), text: `${item.type.toLowerCase()} iteration=${item.iteration} ${item.content}` }));
  run.ledger.forEach(item => lines.push({ kind: "POLICY", time: time(item.recordedAt), text: `event=${item.eventType} mode=${item.mode} actor=${item.actor} ${item.details}` }));
  return lines.sort((left, right) => left.time.localeCompare(right.time));
}
