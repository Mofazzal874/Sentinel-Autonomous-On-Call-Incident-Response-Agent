package io.mofazzal.sentinel.fleet.application;

import io.mofazzal.sentinel.fleet.api.CatalogContracts;
import io.mofazzal.sentinel.demo.ScenarioTemplate;
import io.mofazzal.sentinel.demo.ScenarioTemplateRepository;
import io.mofazzal.sentinel.fleet.domain.FleetService;
import io.mofazzal.sentinel.fleet.domain.Runbook;
import io.mofazzal.sentinel.fleet.domain.ServiceDependency;
import io.mofazzal.sentinel.fleet.domain.Team;
import io.mofazzal.sentinel.fleet.repository.FleetServiceRepository;
import io.mofazzal.sentinel.fleet.repository.RunbookRepository;
import io.mofazzal.sentinel.fleet.repository.ServiceDependencyRepository;
import io.mofazzal.sentinel.fleet.repository.TeamRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Function;

@Service
public class CatalogAdministrationService {

    private static final int MAX_PAGE_SIZE = 100;
    private final TeamRepository teams;
    private final FleetServiceRepository services;
    private final ServiceDependencyRepository dependencies;
    private final RunbookRepository runbooks;
    private final ScenarioTemplateRepository scenarios;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CatalogAdministrationService(TeamRepository teams, FleetServiceRepository services,
                                        ServiceDependencyRepository dependencies,
                                        RunbookRepository runbooks, ScenarioTemplateRepository scenarios,
                                        JdbcTemplate jdbc, Clock clock) {
        this.teams = teams;
        this.services = services;
        this.dependencies = dependencies;
        this.runbooks = runbooks;
        this.scenarios = scenarios;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN')")
    public CatalogContracts.PageView<CatalogContracts.TeamView> listTeams(
            int page, int size, boolean includeArchived) {
        var pageable = page(page, size, "name");
        Page<Team> result = includeArchived ? teams.findAll(pageable) : teams.findByArchivedAtIsNull(pageable);
        return map(result, this::teamView);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.TeamView createTeam(CatalogContracts.TeamWrite request) {
        Team team = teams.saveAndFlush(new Team(request.name(), request.contactChannel()));
        return teamView(team);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.TeamView updateTeam(UUID id, CatalogContracts.TeamWrite request) {
        Team team = team(id);
        requireVersion(request.version(), team.getVersion());
        team.update(request.name(), request.contactChannel());
        return teamView(teams.saveAndFlush(team));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void archiveTeam(UUID id, long version) {
        Team team = team(id);
        requireVersion(version, team.getVersion());
        team.archive(clock.instant());
        teams.saveAndFlush(team);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN')")
    public CatalogContracts.PageView<CatalogContracts.ServiceView> listServices(
            int page, int size, boolean includeArchived) {
        var pageable = page(page, size, "name");
        Page<FleetService> result = includeArchived
                ? services.findAllBy(pageable)
                : services.findByArchivedAtIsNull(pageable);
        return map(result, this::serviceView);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.ServiceView createService(CatalogContracts.ServiceWrite request) {
        Team owner = activeTeam(request.ownerTeamId());
        FleetService service = new FleetService(
                request.name(), owner, request.tier(), request.allowedActions());
        return serviceView(services.saveAndFlush(service));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.ServiceView updateService(UUID id, CatalogContracts.ServiceWrite request) {
        FleetService service = activeService(id);
        requireVersion(request.version(), service.getVersion());
        service.update(request.name(), activeTeam(request.ownerTeamId()),
                request.tier(), request.allowedActions());
        return serviceView(services.saveAndFlush(service));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void archiveService(UUID id, long version) {
        FleetService service = activeService(id);
        requireVersion(version, service.getVersion());
        service.archive(clock.instant());
        services.saveAndFlush(service);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN')")
    public CatalogContracts.PageView<CatalogContracts.DependencyView> listDependencies(int page, int size) {
        return map(dependencies.findAllBy(page(page, size, "createdAt")), this::dependencyView);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.DependencyView createDependency(CatalogContracts.DependencyCreate request) {
        if (request.callerServiceId().equals(request.dependencyServiceId())) {
            throw new IllegalArgumentException("a service cannot depend on itself");
        }
        if (dependencies.existsByCallerServiceIdAndDependencyServiceId(
                request.callerServiceId(), request.dependencyServiceId())) {
            throw new CatalogConflictException("service dependency already exists");
        }
        ServiceDependency dependency = new ServiceDependency(
                activeService(request.callerServiceId()),
                activeService(request.dependencyServiceId()),
                request.criticality(), clock.instant());
        return dependencyView(dependencies.saveAndFlush(dependency));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteDependency(UUID id, long version) {
        ServiceDependency dependency = dependencies.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("service dependency not found"));
        requireVersion(version, dependency.getVersion());
        dependencies.delete(dependency);
        dependencies.flush();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN')")
    public CatalogContracts.PageView<CatalogContracts.RunbookView> listRunbooks(
            int page, int size, boolean includeArchived) {
        var pageable = page(page, size, "title");
        Page<Runbook> result = includeArchived
                ? runbooks.findAll(pageable)
                : runbooks.findByArchivedAtIsNull(pageable);
        return map(result, this::runbookView);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.RunbookView createRunbook(CatalogContracts.RunbookWrite request) {
        Runbook runbook = new Runbook(request.title(), request.symptomDescription(),
                request.steps(), request.actionType());
        return runbookView(runbooks.saveAndFlush(runbook));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.RunbookView updateRunbook(UUID id, CatalogContracts.RunbookWrite request) {
        Runbook runbook = activeRunbook(id);
        requireVersion(request.version(), runbook.getVersion());
        runbook.update(request.title(), request.symptomDescription(), request.steps(), request.actionType());
        runbooks.saveAndFlush(runbook);
        jdbc.update("DELETE FROM runbook_embedding WHERE runbook_id = ?", id);
        return runbookView(runbook);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void archiveRunbook(UUID id, long version) {
        Runbook runbook = activeRunbook(id);
        requireVersion(version, runbook.getVersion());
        runbook.archive(clock.instant());
        runbooks.saveAndFlush(runbook);
        jdbc.update("DELETE FROM runbook_embedding WHERE runbook_id = ?", id);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('VIEWER','SRE_APPROVER','ADMIN')")
    public CatalogContracts.PageView<CatalogContracts.ScenarioView> listScenarios(
            int page, int size, boolean includeArchived) {
        var pageable = page(page, size, "displayName");
        Page<ScenarioTemplate> result = includeArchived
                ? scenarios.findAllBy(pageable)
                : scenarios.findByArchivedAtIsNull(pageable);
        return map(result, this::scenarioView);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.ScenarioView createScenario(CatalogContracts.ScenarioWrite request) {
        ScenarioTemplate scenario = new ScenarioTemplate(request.scenarioKey(), request.displayName(),
                request.description(), request.scenarioType(), activeService(request.serviceId()),
                request.severity(), request.enabled(), clock.instant());
        return scenarioView(scenarios.saveAndFlush(scenario));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogContracts.ScenarioView updateScenario(UUID id, CatalogContracts.ScenarioWrite request) {
        ScenarioTemplate scenario = activeScenario(id);
        requireVersion(request.version(), scenario.getVersion());
        scenario.update(request.scenarioKey(), request.displayName(), request.description(),
                request.scenarioType(), activeService(request.serviceId()), request.severity(),
                request.enabled(), clock.instant());
        return scenarioView(scenarios.saveAndFlush(scenario));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void archiveScenario(UUID id, long version) {
        ScenarioTemplate scenario = activeScenario(id);
        requireVersion(version, scenario.getVersion());
        scenario.archive(clock.instant());
        scenarios.saveAndFlush(scenario);
    }

    private Team team(UUID id) {
        return teams.findById(id).orElseThrow(() -> new CatalogNotFoundException("team not found"));
    }

    private Team activeTeam(UUID id) {
        Team team = team(id);
        if (team.getArchivedAt() != null) {
            throw new CatalogConflictException("team is archived");
        }
        return team;
    }

    private FleetService activeService(UUID id) {
        FleetService service = services.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("service not found"));
        if (service.getArchivedAt() != null) {
            throw new CatalogConflictException("service is archived");
        }
        return service;
    }

    private Runbook activeRunbook(UUID id) {
        Runbook runbook = runbooks.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("runbook not found"));
        if (runbook.getArchivedAt() != null) {
            throw new CatalogConflictException("runbook is archived");
        }
        return runbook;
    }

    private ScenarioTemplate activeScenario(UUID id) {
        ScenarioTemplate scenario = scenarios.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("scenario template not found"));
        if (scenario.getArchivedAt() != null) {
            throw new CatalogConflictException("scenario template is archived");
        }
        return scenario;
    }

    private static PageRequest page(int page, int size, String property) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(property).ascending());
    }

    private static void requireVersion(Long supplied, long actual) {
        if (supplied == null) {
            throw new IllegalArgumentException("version is required for updates");
        }
        requireVersion(supplied.longValue(), actual);
    }

    private static void requireVersion(long supplied, long actual) {
        if (supplied != actual) {
            throw new CatalogConflictException(
                    "stale version: expected " + actual + " but received " + supplied);
        }
    }

    private CatalogContracts.TeamView teamView(Team team) {
        return new CatalogContracts.TeamView(team.getId(), team.getName(), team.getContactChannel(),
                team.getArchivedAt(), team.getVersion());
    }

    private CatalogContracts.ServiceView serviceView(FleetService service) {
        return new CatalogContracts.ServiceView(service.getId(), service.getName(),
                service.getOwnerTeam().getId(), service.getOwnerTeam().getName(), service.getTier(),
                service.getAllowedActions(), service.getArchivedAt(), service.getVersion());
    }

    private CatalogContracts.DependencyView dependencyView(ServiceDependency dependency) {
        return new CatalogContracts.DependencyView(dependency.getId(),
                dependency.getCallerService().getId(), dependency.getCallerService().getName(),
                dependency.getDependencyService().getId(), dependency.getDependencyService().getName(),
                dependency.getCriticality(), dependency.getCreatedAt(), dependency.getVersion());
    }

    private CatalogContracts.RunbookView runbookView(Runbook runbook) {
        return new CatalogContracts.RunbookView(runbook.getId(), runbook.getTitle(),
                runbook.getSymptomDescription(), runbook.getSteps(), runbook.getActionType(),
                runbook.getArchivedAt(), runbook.getVersion());
    }

    private CatalogContracts.ScenarioView scenarioView(ScenarioTemplate scenario) {
        return new CatalogContracts.ScenarioView(scenario.getId(), scenario.getScenarioKey(),
                scenario.getDisplayName(), scenario.getDescription(), scenario.getScenarioType(),
                scenario.getService().getId(), scenario.getService().getName(), scenario.getSeverity(),
                scenario.isEnabled(), scenario.getCreatedAt(), scenario.getUpdatedAt(),
                scenario.getArchivedAt(), scenario.getVersion());
    }

    private static <S, T> CatalogContracts.PageView<T> map(Page<S> source, Function<S, T> mapper) {
        return new CatalogContracts.PageView<>(source.getContent().stream().map(mapper).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
