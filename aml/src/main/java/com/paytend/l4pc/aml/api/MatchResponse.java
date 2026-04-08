package com.paytend.l4pc.aml.api;

import java.time.Instant;

public record MatchResponse(
        String match_id,
        String provider,
        int match_score,
        String match_type,
        String category,
        String entity_id,
        String entity_name,
        String entity_type,
        Instant created_at
) {
}
