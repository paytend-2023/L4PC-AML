package com.paytend.l4pc.aml.model;

import java.time.Instant;
import java.util.List;

/**
 * Unified AML evaluation output. PC-AML returns signals only — L3 makes the final decision.
 *
 * Key design:
 * - hit / no-hit with typed hits
 * - evidence_ref for audit trail
 * - reason_code for machine-readable categorization
 * - restriction_intent as a suggestion (not execution)
 * - manual_review_required flag
 */
public record AmlEvaluationResponse(
        String evaluationId,
        String customerId,
        String traceId,
        boolean hit,
        List<AmlHit> hits,
        RestrictionIntent restrictionIntent,
        boolean manualReviewRequired,
        String overallRiskLevel,
        int riskScore,
        String evidenceRef,
        Instant evaluatedAt
) {

    public record AmlHit(
            HitType hitType,
            String reasonCode,
            String entityId,
            String entityName,
            String provider,
            int matchScore,
            String category,
            String evidenceRef,
            String detail
    ) {}

    public enum HitType {
        SANCTION,
        PEP,
        BLACKLIST_USER,
        BLACKLIST_COUNTERPARTY,
        HIGH_RISK_COUNTRY,
        UNACCEPTABLE_COUNTRY,
        ORG_REGISTRATION_RESTRICTED,
        WATCHLIST,
        ADVERSE_MEDIA
    }

    public enum RestrictionIntent {
        NONE,
        ALLOW,
        REVIEW,
        BLOCK,
        ESCALATE
    }
}
