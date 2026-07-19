# Incident-investigation product research — July 2026

## Research question

What must Sentinel provide to be useful to an on-call engineer rather than merely demonstrate that an agent pipeline exists?

This review deliberately used primary product documentation published or updated after June 2025. It is a product-shaping input, not a claim that Sentinel has feature parity with commercial platforms.

## Current product patterns

### Google Cloud Assist investigations

Google structures an investigation around an issue, bounded time, relevant resources, observations, one or more root-cause hypotheses, and recommended verification or remediation. Operators can revise the input and compare investigation revisions.

Source: <https://cloud.google.com/gemini/docs/cloud-assist/create-investigation>

### AWS DevOps Agent

AWS supports three entry paths: connected tickets, webhooks, and manual investigation. Manual creation includes a free-form issue, starting signal, incident time, priority, and name. The agent constructs a plan, collects findings, identifies root cause, and offers a mitigation plan. Its operations model also uses application topology and correlates metrics, logs, traces, code changes, and deployment history.

Sources:

- <https://docs.aws.amazon.com/devopsagent/latest/userguide/production-operations-autonomous-incident-response.html>
- <https://docs.aws.amazon.com/devopsagent/latest/userguide/working-with-devops-agent-production-operations-index.html>

### Azure SRE Agent

Azure frames the user problem as a tired engineer switching among incident, metrics, logs, chat, and runbook tools. The agent acknowledges the incident, gathers observability data, checks changes and memory, forms and validates hypotheses, and proposes or performs a fix according to a separately configured run mode. Permissions and approval mode are distinct conditions.

Sources:

- <https://learn.microsoft.com/en-us/azure/sre-agent/incident-response>
- <https://learn.microsoft.com/en-us/azure/sre-agent/agent-run-modes>

### Datadog Bits Investigation

Datadog exposes the investigative work: conclusion, key evidence, queries, embedded results, analysis per step, runbook/knowledge sources, actions, and feedback. It treats runbooks, environment context, and corrections from prior investigations as separate knowledge sources.

Sources:

- <https://docs.datadoghq.com/bits_ai/bits_investigation/>
- <https://docs.datadoghq.com/bits_ai/bits_security_analyst/>
- <https://docs.datadoghq.com/bits_ai/bits_investigation/knowledge_sources/>

## Grounded product decision

Sentinel's first live UI had the pipeline but not the operator loop. The corrected loop is:

```text
Define scope
  -> create a durable incident
  -> inspect raw evidence and the queries that produced it
  -> compare the likely cause with alternatives
  -> follow verification steps
  -> review one bounded remediation and its risk
  -> observe the deterministic safety decision
  -> preserve or share the investigation record
```

## Public-demo boundary

The public environment must be useful without becoming a remote execution service.

- Visitors may choose from active seeded services and bounded incident dimensions.
- The backend generates and persists causal synthetic telemetry from those dimensions.
- Visitors may inspect evidence, AI analysis, recommendations, and safety outcomes.
- Visitors may not provide commands, tool arguments, policies, credentials, or action approvals.
- Infrastructure mutation remains dry-run.
- The existing signed webhook and protected control-plane APIs remain the path for real integrations.

## Implementation consequences

1. Replace a small set of opaque scenario buttons with a parameterized investigation composer.
2. Persist the chosen service, symptom, severity, impact, signal strength, and deployment context.
3. Return bounded raw metrics, logs, deployments, runbook grounding, model transcript, and policy ledger.
4. Render both a readable operator report and a terminal-like evidence stream.
5. Teach integration and investigation in the product, not only in repository Markdown.
6. Treat light/dark theme as a preference, not product value; it must not displace the investigation work.
