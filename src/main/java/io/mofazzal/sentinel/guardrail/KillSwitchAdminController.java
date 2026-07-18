package io.mofazzal.sentinel.guardrail;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/kill-switch")
public class KillSwitchAdminController {

    private final KillSwitchAdministrationService administration;

    public KillSwitchAdminController(KillSwitchAdministrationService administration) {
        this.administration = administration;
    }

    @PutMapping
    public ResponseEntity<Map<String, Boolean>> update(@RequestBody KillSwitchRequest request,
                                                       Authentication authentication) {
        boolean engaged = administration.setEngaged(request.engaged(), authentication.getName());
        return ResponseEntity.ok(Map.of("engaged", engaged));
    }
}
