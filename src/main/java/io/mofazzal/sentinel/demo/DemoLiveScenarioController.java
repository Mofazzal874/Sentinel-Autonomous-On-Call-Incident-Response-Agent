package io.mofazzal.sentinel.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo")
public class DemoLiveScenarioController {

    private final DemoLiveScenarioService scenarios;
    private final DemoSystemOverviewService overview;

    public DemoLiveScenarioController(DemoLiveScenarioService scenarios, DemoSystemOverviewService overview) {
        this.scenarios = scenarios;
        this.overview = overview;
    }

    @GetMapping("/overview")
    public DemoSystemOverview overview() {
        return overview.read();
    }

    @GetMapping("/scenarios")
    public List<DemoScenarioTemplateView> list() {
        return scenarios.listTemplates();
    }

    @PostMapping("/scenarios/{templateId}/runs")
    public ResponseEntity<DemoLiveSubmissionView> submit(
            @PathVariable UUID templateId,
            @RequestHeader("Idempotency-Key") @Size(max = 100) String idempotencyKey,
            HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String client = forwarded == null ? request.getRemoteAddr() : forwarded.split(",", 2)[0];
        return ResponseEntity.accepted().body(scenarios.submit(templateId, idempotencyKey, client));
    }

    @GetMapping("/submissions/{publicId}")
    public DemoLiveSubmissionView status(@PathVariable UUID publicId) {
        return scenarios.status(publicId);
    }

    @ExceptionHandler(DemoSandboxLimitException.class)
    ResponseEntity<Map<String, String>> limit(DemoSandboxLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("code", exception.code(), "message", exception.getMessage()));
    }

    @ExceptionHandler(DemoScenarioNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(DemoScenarioNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST", "message", exception.getMessage()));
    }
}
