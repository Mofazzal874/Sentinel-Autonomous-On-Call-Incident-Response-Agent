"use client";

import { useEffect, useState } from "react";
import { ArrowRight, BookOpen, CheckCircle2, CircleHelp, Database, GitBranch, ShieldCheck, Terminal, XCircle } from "lucide-react";

const modules = [
  {
    title: "The 3 a.m. problem", eyebrow: "START HERE", icon: CircleHelp,
    lead: "An alert proves that a threshold fired. It does not explain the customer impact, the cause, or the safest next move.",
    sections: [
      ["What the operator needs", "One place to identify the affected service, inspect time-bounded metrics and logs, correlate recent changes, compare a runbook, and preserve the decision for the next engineer."],
      ["What Sentinel changes", "It turns scattered signals into a durable investigation. The model helps synthesize evidence; deterministic Java policy owns authorization. This shortens investigation without handing control to probabilistic output."],
      ["Real use case", "A release finishes at 10:02 and payment failures rise at 10:04. Sentinel surfaces the temporal correlation, finds the approved rollback runbook, proposes rollback, and checks risk, allowlists, kill switch, approval, grounding, and idempotency before any executor can run."],
    ],
    quiz: ["What does an alert tell you by itself?", ["The exact root cause", "That a detection condition fired", "Which command to execute"], 1, "Correct: detection starts an investigation; it is not a diagnosis."],
  },
  {
    title: "Create an investigation", eyebrow: "HANDS-ON WORKFLOW", icon: GitBranch,
    lead: "A useful incident tool starts with operator context—not an unrestricted chatbot prompt.",
    sections: [
      ["Choose scope", "Select a seeded service, observed failure, severity, signal strength, customer impact, and whether a recent deployment exists. These fields become persisted facts with generated identifiers."],
      ["Why choices are bounded", "A public visitor can explore meaningful combinations, but cannot submit shell commands, URLs, SQL, or infrastructure targets. This preserves realism without turning the portfolio demo into a remote-control surface."],
      ["What happens on Create", "The API requires an idempotency key, writes a durable submission, creates telemetry in PostgreSQL, publishes an alert through RabbitMQ, and returns a public investigation ID that can be revisited."],
    ],
    quiz: ["Why does the request carry an idempotency key?", ["To encrypt the payload", "To make the UI faster", "To prevent a retry from duplicating work"], 2, "Correct: the database uniqueness boundary makes retries safe."],
  },
  {
    title: "Read the raw evidence", eyebrow: "OBSERVABILITY", icon: Terminal,
    lead: "A conclusion is defensible only when the reviewer can inspect the observations and queries behind it.",
    sections: [
      ["Metrics", "Sentinel reads five named time series inside an indexed, bounded window. The console shows sample counts, minimum, maximum, and latest values instead of loading all telemetry into application memory."],
      ["Logs and traces", "Log rows include timestamp, level, message, and trace ID. They are synthetic operational facts stored in PostgreSQL; filtering the console never changes the underlying investigation."],
      ["Deployments and runbooks", "A recent release is evidence, not automatic proof. The agent also retrieves an approved runbook whose symptom and action are shown beside the proposed remediation."],
    ],
    quiz: ["Does a deployment immediately before an error prove causation?", ["Yes, always", "No; it is a hypothesis that needs corroborating evidence", "Only for SEV1"], 1, "Correct: temporal correlation is useful evidence, but not proof by itself."],
  },
  {
    title: "Understand the AI", eyebrow: "PROPOSE, NEVER AUTHORIZE", icon: BookOpen,
    lead: "Sentinel uses AI to organize and critique a proposal. It deliberately keeps AI out of the safety-critical decision path.",
    sections: [
      ["Router", "Classifies the incident and selects only the bounded read tools needed for this investigation."],
      ["Grounded proposal", "The generator receives retrieved metrics, logs, deployments, and runbooks. Missing authoritative evidence causes escalation rather than an invented answer."],
      ["Evaluator loop", "A bounded evaluator critiques grounding and consistency. Iteration limits, rate limits, and persisted transcript entries prevent an open-ended model loop."],
    ],
    quiz: ["Who decides whether a remediation may execute?", ["The language model", "The frontend", "The deterministic GuardrailGate"], 2, "Correct: the model can propose; Java policy authorizes or refuses."],
  },
  {
    title: "Defend the safety boundary", eyebrow: "CONTROL PLANE", icon: ShieldCheck,
    lead: "Automation is valuable only when its authority, failure modes, and audit trail are explicit.",
    sections: [
      ["Single gate", "Every mutation strategy has one route through the GuardrailGate. Kill switch, service allowlist, action allowlist, risk, grounding, human approval, and idempotency are deterministic checks."],
      ["Dry-run demo", "This public deployment evaluates and records the proposed action but never changes infrastructure. The result proves the decision path without exposing production authority."],
      ["Append-only history", "An action is recorded as a new ledger fact. A later compensation refers to the original action rather than deleting it, which preserves accountability during retries and recovery."],
    ],
    quiz: ["Can human approval bypass the kill switch?", ["Yes", "No", "Only for administrators"], 1, "Correct: approval does not weaken hard safety invariants."],
  },
  {
    title: "From sandbox to production", eyebrow: "INTEGRATION MAP", icon: Database,
    lead: "The demo supplies safe synthetic inputs. A production installation replaces adapters—not the safety model.",
    sections: [
      ["Connect signals", "Map Prometheus-compatible metrics, centralized logs, deployment events, and an alert webhook into the existing bounded tool contracts. Kubernetes and Grafana are integrations, not requirements for understanding this demo."],
      ["Connect identity", "Use JWT roles for viewers, approvers, administrators, and the under-privileged agent service account. Store secrets outside Git and restrict webhook ingress with HMAC, mTLS, or network policy."],
      ["Graduate carefully", "Keep dry-run on while replaying an offline incident corpus. Then allowlist one idempotent, low-risk strategy, observe it, require approvals where appropriate, and expand only with evidence."],
    ],
    quiz: ["What should be enabled first in a production rollout?", ["Every remediation", "One allowlisted low-risk strategy in dry-run", "Unrestricted model tools"], 1, "Correct: narrow authority and measured expansion limit blast radius."],
  },
] as const;

export default function LearningCenter({ onLab, onIncidents }: { onLab: () => void; onIncidents: () => void }) {
  const [open, setOpen] = useState(0);
  const [answers, setAnswers] = useState<Record<number, number>>({});
  useEffect(() => { const saved = window.localStorage.getItem("sentinel-learning-progress"); if (saved) setAnswers(JSON.parse(saved)); }, []);
  const choose = (answer: number) => { const next = { ...answers, [open]: answer }; setAnswers(next); window.localStorage.setItem("sentinel-learning-progress", JSON.stringify(next)); };
  const lesson = modules[open]; const Icon = lesson.icon; const correct = answers[open] === lesson.quiz[2];
  return <section className="pageSection learnPage"><div className="pageIntro"><p>INTERACTIVE DOCUMENTATION</p><h1>Learn the system by<br /><em>operating it.</em></h1><span>Six guided modules explain the user problem, data flow, AI boundary, safety design, and production path. Your progress stays in this browser.</span><div className="learningProgress"><i style={{ width: `${Object.keys(answers).length / modules.length * 100}%` }} /><span>{Object.keys(answers).length} of {modules.length} knowledge checks attempted</span></div></div>
    <div className="learningLayout"><aside><span>LEARNING PATH</span>{modules.map((item,index) => <button className={open === index ? "active" : ""} onClick={() => setOpen(index)} key={item.title}><small>{answers[index] === item.quiz[2] ? "✓" : `0${index + 1}`}</small>{item.title}</button>)}</aside>
      <article className="lesson"><header><Icon /><div><span>{lesson.eyebrow} · MODULE {open + 1}</span><h2>{lesson.title}</h2></div></header><p className="lessonLead">{lesson.lead}</p><div className="lessonSections">{lesson.sections.map(([title,body]) => <section key={title}><h3>{title}</h3><p>{body}</p></section>)}</div>
        <div className="knowledgeCheck"><span>KNOWLEDGE CHECK</span><h3>{lesson.quiz[0]}</h3><div>{lesson.quiz[1].map((answer,index) => <button className={answers[open] === index ? (index === lesson.quiz[2] ? "correct" : "wrong") : ""} onClick={() => choose(index)} key={answer}>{answers[open] === index ? (index === lesson.quiz[2] ? <CheckCircle2 /> : <XCircle />) : <i />}{answer}</button>)}</div>{answers[open] !== undefined && <p className={correct ? "correct" : "wrong"}>{correct ? lesson.quiz[3] : "Not quite. Re-read the module and try another answer."}</p>}</div>
        <div className="lessonNav"><button disabled={open === 0} onClick={() => setOpen(open - 1)}>Previous</button>{open < modules.length - 1 ? <button onClick={() => setOpen(open + 1)}>Next module <ArrowRight /></button> : <button onClick={onLab}>Create an investigation <ArrowRight /></button>}</div>
      </article></div>
    <div className="practiceRoute"><div><p>PUT THE MODEL INTO PRACTICE</p><h2>Use the same two views an operator uses.</h2></div><button onClick={onLab}>1. Create an incident</button><button onClick={onIncidents}>2. Inspect the evidence</button></div>
    <div className="glossary"><div><p>PLAIN-LANGUAGE GLOSSARY</p><h2>Words used in the console</h2></div><Glossary term="Grounding" meaning="Supporting a proposal with retrieved operational evidence instead of model memory." /><Glossary term="Idempotency" meaning="Repeating the same request cannot create a duplicate incident or action." /><Glossary term="Dry-run" meaning="Evaluate and record an action without changing infrastructure." /><Glossary term="Compensation" meaning="A new action that reverses an earlier action without erasing history." /></div>
  </section>;
}

function Glossary({ term, meaning }: { term: string; meaning: string }) { return <article><strong>{term}</strong><p>{meaning}</p></article>; }
