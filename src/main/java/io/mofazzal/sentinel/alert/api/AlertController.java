package io.mofazzal.sentinel.alert.api;

import io.mofazzal.sentinel.alert.application.AlertIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertIngestionService ingestionService;

    public AlertController(AlertIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<AlertAcknowledgement> ingest(
            @Valid @RequestBody AlertPayload payload,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 200) String idempotencyKey) {
        return ResponseEntity.accepted().body(ingestionService.ingest(payload, idempotencyKey));
    }
}
