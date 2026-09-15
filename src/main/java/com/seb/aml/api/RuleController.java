package com.seb.aml.api;

import com.seb.aml.api.dto.RuleResponse;
import com.seb.aml.rule.Rule;
import com.seb.aml.screening.ScreeningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for rule introspection.
 *
 * <p>Provides a read-only view of all registered rules and their current configuration.
 * This endpoint is useful for debugging, monitoring, and as a foundation for a future
 * rule management UI.</p>
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final ScreeningService screeningService;

    public RuleController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    /**
     * List all registered rules and their configuration.
     *
     * @return list of rules with their parameters, enabled status, and descriptions
     */
    @GetMapping
    public ResponseEntity<Map<String, List<RuleResponse>>> listRules() {
        List<RuleResponse> rules = screeningService.getRegisteredRules().stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(Map.of("rules", rules));
    }

    private RuleResponse toResponse(Rule rule) {
        return new RuleResponse(
                rule.getRuleId(),
                rule.getDescription(),
                rule.isEnabled(),
                rule.getParameters()
        );
    }
}
