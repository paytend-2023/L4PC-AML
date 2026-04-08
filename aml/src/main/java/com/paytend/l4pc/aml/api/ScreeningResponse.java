package com.paytend.l4pc.aml.api;

import java.time.Instant;
import java.util.List;

public record ScreeningResponse(
        String screening_id,
        String customer_id,
        String screening_type,
        String status,
        String risk_level,
        int risk_score,
        int match_count,
        List<MatchResponse> matches,
        Instant created_at
) {
}
