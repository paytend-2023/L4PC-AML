package com.paytend.l4pc.aml.api;

import java.time.Instant;
import java.util.List;

public record AlertResponse(
        String alert_id,
        String screening_id,
        String customer_id,
        String alert_type,
        String risk_level,
        String status,
        String assigned_to,
        List<CaseDecisionResponse> decisions,
        Instant created_at,
        Instant updated_at
) {
}
