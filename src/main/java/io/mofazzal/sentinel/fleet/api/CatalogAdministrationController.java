package io.mofazzal.sentinel.fleet.api;

import io.mofazzal.sentinel.fleet.application.CatalogAdministrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogAdministrationController {

    private final CatalogAdministrationService catalog;

    public CatalogAdministrationController(CatalogAdministrationService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/teams")
    public CatalogContracts.PageView<CatalogContracts.TeamView> teams(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return catalog.listTeams(page, size, includeArchived);
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogContracts.TeamView createTeam(@Valid @RequestBody CatalogContracts.TeamWrite request) {
        return catalog.createTeam(request);
    }

    @PutMapping("/teams/{id}")
    public CatalogContracts.TeamView updateTeam(
            @PathVariable UUID id, @Valid @RequestBody CatalogContracts.TeamWrite request) {
        return catalog.updateTeam(id, request);
    }

    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveTeam(@PathVariable UUID id, @RequestParam long version) {
        catalog.archiveTeam(id, version);
    }

    @GetMapping("/services")
    public CatalogContracts.PageView<CatalogContracts.ServiceView> services(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return catalog.listServices(page, size, includeArchived);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogContracts.ServiceView createService(@Valid @RequestBody CatalogContracts.ServiceWrite request) {
        return catalog.createService(request);
    }

    @PutMapping("/services/{id}")
    public CatalogContracts.ServiceView updateService(
            @PathVariable UUID id, @Valid @RequestBody CatalogContracts.ServiceWrite request) {
        return catalog.updateService(id, request);
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveService(@PathVariable UUID id, @RequestParam long version) {
        catalog.archiveService(id, version);
    }

    @GetMapping("/dependencies")
    public CatalogContracts.PageView<CatalogContracts.DependencyView> dependencies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.listDependencies(page, size);
    }

    @PostMapping("/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogContracts.DependencyView createDependency(
            @Valid @RequestBody CatalogContracts.DependencyCreate request) {
        return catalog.createDependency(request);
    }

    @DeleteMapping("/dependencies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDependency(@PathVariable UUID id, @RequestParam long version) {
        catalog.deleteDependency(id, version);
    }

    @GetMapping("/runbooks")
    public CatalogContracts.PageView<CatalogContracts.RunbookView> runbooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return catalog.listRunbooks(page, size, includeArchived);
    }

    @PostMapping("/runbooks")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogContracts.RunbookView createRunbook(@Valid @RequestBody CatalogContracts.RunbookWrite request) {
        return catalog.createRunbook(request);
    }

    @PutMapping("/runbooks/{id}")
    public CatalogContracts.RunbookView updateRunbook(
            @PathVariable UUID id, @Valid @RequestBody CatalogContracts.RunbookWrite request) {
        return catalog.updateRunbook(id, request);
    }

    @DeleteMapping("/runbooks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveRunbook(@PathVariable UUID id, @RequestParam long version) {
        catalog.archiveRunbook(id, version);
    }

    @GetMapping("/scenarios")
    public CatalogContracts.PageView<CatalogContracts.ScenarioView> scenarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return catalog.listScenarios(page, size, includeArchived);
    }

    @PostMapping("/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogContracts.ScenarioView createScenario(
            @Valid @RequestBody CatalogContracts.ScenarioWrite request) {
        return catalog.createScenario(request);
    }

    @PutMapping("/scenarios/{id}")
    public CatalogContracts.ScenarioView updateScenario(
            @PathVariable UUID id, @Valid @RequestBody CatalogContracts.ScenarioWrite request) {
        return catalog.updateScenario(id, request);
    }

    @DeleteMapping("/scenarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveScenario(@PathVariable UUID id, @RequestParam long version) {
        catalog.archiveScenario(id, version);
    }
}
