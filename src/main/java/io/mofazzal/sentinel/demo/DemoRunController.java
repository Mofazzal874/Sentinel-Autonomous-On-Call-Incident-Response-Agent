package io.mofazzal.sentinel.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo/runs")
public class DemoRunController {

    private final DemoRunQueryService queries;

    public DemoRunController(DemoRunQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<DemoRunSummary> list() {
        return queries.list();
    }

    @GetMapping("/{publicId}")
    public DemoRunView find(@PathVariable UUID publicId) {
        return queries.find(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown demo run"));
    }
}
