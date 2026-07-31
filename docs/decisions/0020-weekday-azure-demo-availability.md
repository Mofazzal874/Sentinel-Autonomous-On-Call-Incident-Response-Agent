# ADR 0020: Weekday Azure demo availability

## Status

Accepted for owner-authorized deployment; Azure activation evidence pending.

## Context

The manually invoked two-hour session minimized cost but made the portfolio URL unavailable until the owner intervened. The owner requested unattended availability from 10:00 through 18:00 Bangladesh time, excluding Saturday and Sunday.

Starting a VM is a spending authority. Combining start and stop in one identity or one long-running workflow would increase blast radius and make shutdown dependent on the success of the earlier start run. GitHub deployment and public visitors must continue to have no start authority.

## Decision

Create two Azure Consumption Logic Apps with fixed UTC recurrence schedules:

- `sentinel-demo-weekday-start` requests start at 03:50 UTC, Monday-Friday, which is 09:50 in Bangladesh;
- `sentinel-demo-weekday-stop` requests deallocation at 12:00 UTC, Monday-Friday, which is 18:00 in Bangladesh.

The ten-minute lead allows VM, Docker, and Spring Boot initialization before the 10:00 availability target. Bangladesh does not currently observe daylight saving time, so fixed UTC avoids time-zone identifier ambiguity.

Each workflow has its own system-assigned identity and custom role scoped to the exact VM. The starter receives read and start only. The stopper receives read and deallocate only. An explicit historical recurrence anchor prevents workflow creation from triggering an immediate run. Provisioning reads back and validates recurrence, operation URI, managed identity, role actions, scope, and assignment.

## Consequences

- The stable URL is normally available on weekdays during the approved window without owner intervention.
- Weekends and off-hours remain deallocated unless the owner uses the private bounded-session callback.
- Independent shutdown still runs if application startup or the start workflow fails.
- A 09:50-18:00 weekday costs approximately `$0.6978` using the reviewed July 2026 rates. A modeled 22-weekday month is approximately `$17.72`, excluding taxes and Logic App operations. This intentionally supersedes the earlier `$0.50` active-day target.
- The `$10` budget remains an alert goal rather than a hard cap and is insufficient for a full month of this schedule.
