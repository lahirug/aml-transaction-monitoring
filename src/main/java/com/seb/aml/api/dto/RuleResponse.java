package com.seb.aml.api.dto;

import java.util.Map;

/**
 * Response DTO representing a single rule's configuration in the rules listing endpoint.
 *
 * @param ruleId unique identifier for the rule
 * @param description human-readable description of what the rule detects
 * @param enabled whether the rule is currently active
 * @param parameters the rule's current configuration parameters
 */
public record RuleResponse(
        String ruleId,
        String description,
        boolean enabled,
        Map<String, Object> parameters
) {
}
