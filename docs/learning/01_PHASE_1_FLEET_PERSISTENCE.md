# Phase 1: Fleet Persistence and the First Read API

This lesson explains the first complete vertical slice of Sentinel. It assumes no prior Spring Data JPA knowledge.

## 1. What we built

Phase 1 stores a simulated production fleet in PostgreSQL and exposes a read-only list of services.

```text
HTTP GET /api/v1/fleet/services
        |
        v
FleetController
        |
        v
FleetQueryService  [read-only transaction]
        |
        v
FleetServiceRepository  [explicit entity graph]
        |
        v
Hibernate/JPA -> PostgreSQL 17
```

The database also holds deployments, metric samples, log events, runbooks, and an incident skeleton. Those records are evidence that later deterministic tools and the agent will query.

## 2. Prerequisite vocabulary

- **Relational database:** data organized into tables with constraints and relationships.
- **JDBC:** Java's low-level standard for communicating with SQL databases.
- **JPA:** a Java specification for mapping objects to relational tables.
- **Hibernate:** the JPA implementation used by Spring Boot here.
- **Entity:** a Java object whose identity and state are persisted in a table.
- **Repository:** an interface that represents persistence operations and queries.
- **Transaction:** an all-or-nothing unit of database work.
- **Migration:** a versioned, repeatable change to the database schema.
- **DTO:** a data shape intentionally designed to cross an API boundary.

JPA is not the database. It translates between Java entities and SQL. PostgreSQL still owns storage, constraints, indexes, transactions, and query execution.

## 3. Infrastructure: why three containers already exist

`compose.yaml` defines PostgreSQL/pgvector, Redis, and RabbitMQ. Only PostgreSQL is used by application code in Phase 1. Redis and RabbitMQ are started and health-checked now so Phase 2 can use a verified baseline without installing Windows services.

Each service has a named volume:

```text
container can be recreated
        |
        v
named volume keeps state in Docker's E:-backed data disk
```

Sentinel publishes PostgreSQL on host port `55432`, not the usual `5432`. An existing `postgres.exe` already owns IPv4 port `5432`. Changing Sentinel's port isolates the projects and avoids deleting, stopping, or reconfiguring an unknown database.

Inside Docker, PostgreSQL still listens on `5432`:

```text
Spring on Windows -> localhost:55432 -> container:5432
```

## 4. Schema ownership: Flyway first, Hibernate second

The startup sequence is deliberate:

```text
V1/V2 SQL files -> Flyway migrates -> Hibernate validates -> application starts
```

Flyway owns schema changes. `V1__core_schema.sql` creates tables, foreign keys, checks, indexes, and the pgvector extension. `V2__seed_reference.sql` inserts stable teams, services, allowlists, and runbooks.

Hibernate is configured with:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

`validate` means Hibernate compares the entity mapping with the real schema and fails startup if they disagree. It does not silently alter the database.

Why not `ddl-auto: update`?

- The generated change is not reviewed as SQL.
- Different environments can drift.
- Destructive or ambiguous changes are unsafe.
- Roll-forward deployment history is lost.

## 5. Reading the entity model

### Team and service

A service has one owner team, one tier, and a set of permitted remediation types.

```text
team 1 <----- many fleet_service
fleet_service 1 <----- many service_allowed_action
```

`FleetService.ownerTeam` is `@ManyToOne(fetch = LAZY)`. LAZY means Hibernate initially holds a reference and loads the team only when requested inside a valid persistence context.

The remediation set is an `@ElementCollection` because each action is an enum value rather than an independent domain object with its own lifecycle.

### Immutable evidence records

`Deployment`, `MetricSample`, and `LogEvent` have constructors and getters but no public state-changing setters. Operational evidence should be appended as new facts, not rewritten casually.

All timestamps use `Instant`, which represents a point on the UTC timeline. Presentation code can later format it for Dhaka or another timezone without changing stored meaning.

Enums use `EnumType.STRING`:

```text
stored: "SUCCEEDED"  -> readable and stable
not:    0            -> breaks if enum order changes
```

### Incident lifecycle skeleton

An incident starts at `OPEN`. Its domain method permits only explicit transitions:

```text
OPEN -> TRIAGING -> AWAITING_APPROVAL -> REMEDIATING -> RESOLVED
  |          |              |                 |
  +----------+--------------+-----------------> ESCALATED
```

For example, `OPEN -> RESOLVED` is rejected. The database also has an optimistic `version` column so later concurrent incident updates can detect lost-update races.

## 6. Transactions belong in the application service

`FleetQueryService.listServices()` has `@Transactional(readOnly = true)`.

Locally, this keeps the persistence context open while entities are mapped to DTOs. Architecturally, it declares one application operation and its database consistency boundary. The controller does not own the transaction, and DTO serialization happens after mapping rather than lazily querying the database.

`readOnly = true` is a performance and intent hint. It does not replace database authorization and should not be treated as a security boundary.

The synthetic seeder uses a normal read-write `@Transactional` boundary. If any deployment, metric, or log insert fails, the entire scenario rolls back.

## 7. DTOs protect the API boundary

The controller returns `FleetServiceResponse`, not `FleetService`.

This prevents several problems:

- JPA implementation details leaking into JSON.
- Lazy associations unexpectedly executing SQL during serialization.
- Bidirectional relationships causing recursive JSON.
- Database refactors accidentally changing the public API.

The DTO deliberately includes the owner summary and a stable, sorted remediation list.

## 8. N+1 and the explicit fetch plan

The N+1 problem looks like this:

```text
1 query: load 100 services
100 queries: load each owner team
100 queries: load each action collection
```

The endpoint repository uses `@EntityGraph(attributePaths = {"ownerTeam", "allowedActions"})`. Hibernate generated one joined query during verification.

Other valid solutions in different cases include:

- DTO projection directly from a query.
- An explicit `join fetch` query.
- Batch fetching when one large join would multiply too many rows.

Do not solve N+1 by making every association EAGER. That removes control and can create larger load explosions elsewhere.

## 9. Bounded telemetry queries and indexes

Later tools must never call `findAll()` for telemetry. Each query requires:

- a service ID;
- a time boundary or window;
- a metric name where relevant;
- a `Pageable` limit;
- newest-first ordering.

The deployment correlation query asks:

```text
For service S and spike time T,
return at most N deployments where deployed_at <= T,
newest first.
```

Its index is ordered the same way:

```sql
CREATE INDEX ix_deployment_service_time
    ON deployment(service_id, deployed_at DESC);
```

The database can seek to one service and walk backward through time instead of scanning every deployment.

Metric and log tables have equivalent service/time indexes. This matters because telemetry tables can grow much faster than reference tables.

## 10. The synthetic bad-deployment scenario

The `seed` profile inserts a deterministic scenario for `payments-api`:

```text
12:00 bad deployment
12:02 error rate and latency jump
12:03 timeout errors begin
12:04 circuit breaker opens
```

It inserts:

- 2 deployments;
- 63 metric samples across three metric names;
- 5 correlated log events.

Before inserting, it checks the unique bad-deployment Git SHA. The check and all inserts share one transaction, and the database unique constraint is the durable backstop. Two verified starts produced identical row counts.

Reference data belongs in Flyway because it is stable. Demo telemetry belongs in a profile-gated seeder because scenarios must be repeatable and changeable.

## 11. How the tests prove the design

The Testcontainers test starts a disposable real PostgreSQL 17 + pgvector database. It then:

1. applies Flyway V1 and V2;
2. lets Hibernate validate the entities;
3. confirms pgvector 0.8.2 exists;
4. inserts deployments around a spike time;
5. proves the post-spike deployment is excluded;
6. proves the result is newest-first and limited;
7. proves metric and log windows obey their page limit.

An H2 test would be faster but would not prove PostgreSQL extensions, timestamp behavior, indexes, or PostgreSQL-specific SQL.

## 12. Code map

- Infrastructure: `compose.yaml`
- Spring configuration: `src/main/resources/application.yml`
- Schema: `src/main/resources/db/migration/`
- Entities and enums: `src/main/java/io/mofazzal/sentinel/fleet/domain/`
- Incident skeleton: `src/main/java/io/mofazzal/sentinel/incident/domain/`
- Repositories: `src/main/java/io/mofazzal/sentinel/fleet/repository/`
- Seeder: `src/main/java/io/mofazzal/sentinel/fleet/seed/`
- Application transaction: `FleetQueryService`
- HTTP boundary: `FleetController` and `FleetServiceResponse`
- Real-database test: `FleetPersistenceIntegrationTest`

## 13. Commands to practice

```powershell
# Start or reconcile infrastructure and wait for health
docker compose up -d --wait

# Run the application with deterministic demo evidence
.\gradlew.bat bootRun --args="--spring.profiles.active=seed"

# In another terminal
Invoke-RestMethod http://localhost:8080/api/v1/fleet/services

# Run unit and disposable-database integration tests
.\gradlew.bat clean test

# Stop containers but keep named-volume data
docker compose stop
```

Do not use `docker compose down -v` unless you deliberately want to delete the local database, Redis, and RabbitMQ volumes.

## 14. Failure modes to reason about

- **PostgreSQL unavailable:** application startup fails because migrations and validation cannot run.
- **Migration/entity mismatch:** Hibernate validation fails startup.
- **Seeder interrupted:** its transaction rolls back; a later run can retry.
- **Repeated seed start:** the existing Git SHA makes the run a no-op.
- **Lazy association accessed after transaction:** `LazyInitializationException`.
- **No page limit:** a telemetry query can exhaust memory and database capacity.
- **Port collision:** the client may reach the wrong database even while the container itself is healthy.

## 15. Defend This answers

1. `open-in-view: false` prevents the web layer from quietly keeping persistence access open through serialization. Fetch and transaction decisions stay explicit.
2. N+1 is one parent query followed by repeated association queries. Use an entity graph, fetch join, DTO projection, or batching according to result shape.
3. A lazy proxy accessed after its persistence context closes causes `LazyInitializationException`. Fetch what the application operation needs inside its transaction.
4. String enums survive reordering and remain readable; ordinals couple stored data to Java source order.
5. Transactions are on application services. The default propagation is `REQUIRED`: join an existing transaction or start one.
6. Flyway produces versioned, reviewable SQL; Hibernate only validates. Automatic update is not a safe deployment process.
7. At very large scale, retain bounded queries, partition telemetry by time, use appropriate composite/covering or BRIN indexes, archive old data, and select only needed columns.

## 16. Pen-and-paper exercises

1. Draw the GET request from HTTP to PostgreSQL and back. Label the transaction start and end.
2. Write the SQL shape for “three deployments before 12:05” before looking at the repository query.
3. Explain why `(service_id, deployed_at)` is better than two unrelated indexes for this query.
4. Add a fourth service on paper. Decide whether it belongs in a migration or the scenario seeder and justify the choice.
5. Imagine one million metrics per hour. List which data can be summarized, partitioned, archived, or deleted.
6. Explain aloud why returning an entity directly from a controller couples API design to persistence design.
