package io.mofazzal.sentinel.incident.domain;

public enum IncidentStatus {
    OPEN,
    TRIAGING,
    AWAITING_APPROVAL,
    REMEDIATING,
    RESOLVED,
    ESCALATED
}
