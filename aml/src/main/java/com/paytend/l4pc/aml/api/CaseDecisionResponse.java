package com.paytend.l4pc.aml.api;

import java.time.Instant;

public record CaseDecisionResponse(
        String decision_id,
        String decision,
        String decision_reason,
        String decided_by,
        String approved_by,
        Instant created_at
) {
}
