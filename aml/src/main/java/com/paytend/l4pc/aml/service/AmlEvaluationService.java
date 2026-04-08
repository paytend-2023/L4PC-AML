package com.paytend.l4pc.aml.service;

import com.paytend.l4pc.aml.blacklist.BlacklistCheckService;
import com.paytend.l4pc.aml.evidence.AmlEvidenceRecord;
import com.paytend.l4pc.aml.evidence.AmlEvidenceRepository;
import com.paytend.l4pc.aml.model.AmlCheckType;
import com.paytend.l4pc.aml.model.AmlEvaluationRequest;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse.*;
import com.paytend.l4pc.aml.restriction.RestrictionCheckService;
import com.paytend.l4pc.aml.sanctions.SanctionScreeningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unified AML evaluation orchestrator.
 *
 * This is the primary entry point for L3 domain layers to evaluate AML signals.
 * It aggregates results from:
 * 1. Sanctions screening (DowJones, EU, UN, internal watchlist)
 * 2. Blacklist checks (user + counterparty)
 * 3. Country restriction checks
 *
 * And returns a unified AmlEvaluationResponse with:
 * - hit / no-hit
 * - typed hits with reason codes
 * - restriction_intent (NONE / ALLOW / REVIEW / BLOCK / ESCALATE)
 * - evidence references
 * - manual_review_required flag
 *
 * PC-AML outputs signals only. L3 makes the final business decision.
 *
 * Legacy replacement mapping:
 * - TradeCheckService.checkTrade steps 1,2,4,22 → this service
 * - TradeCheckService.checkRiskCountryAmount → RestrictionCheckService
 * - TradeCheckService.checkUserBlack → BlacklistCheckService
 * - TradeCheckService.checkTransferBlackList → BlacklistCheckService
 * - TradeCheckService.dowJonesResult → SanctionScreeningService
 * - DowjonesInfService.sendDowjones → SanctionScreeningService
 * - SalvAmlService.sendCustomer/sendTrade → SanctionScreeningService (future)
 */
@Service
public class AmlEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AmlEvaluationService.class);

    private final SanctionScreeningService sanctionScreeningService;
    private final BlacklistCheckService blacklistCheckService;
    private final RestrictionCheckService restrictionCheckService;
    private final AmlEvidenceRepository evidenceRepository;

    public AmlEvaluationService(SanctionScreeningService sanctionScreeningService,
                                BlacklistCheckService blacklistCheckService,
                                RestrictionCheckService restrictionCheckService,
                                AmlEvidenceRepository evidenceRepository) {
        this.sanctionScreeningService = sanctionScreeningService;
        this.blacklistCheckService = blacklistCheckService;
        this.restrictionCheckService = restrictionCheckService;
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional
    public AmlEvaluationResponse evaluate(AmlEvaluationRequest request) {
        String evaluationId = "aml_" + compactUuid();
        List<AmlHit> allHits = new ArrayList<>();

        // 1. Blacklist checks — customer
        List<AmlHit> customerBlacklistHits = blacklistCheckService.checkCustomer(
                request.customerId(), null, request.customerIdNumber(), null);
        allHits.addAll(customerBlacklistHits);

        // 2. Blacklist checks — counterparty
        if (request.counterpartyAccountNumber() != null || request.counterpartyName() != null) {
            List<AmlHit> counterpartyBlacklistHits = blacklistCheckService.checkCounterparty(
                    request.counterpartyAccountNumber(), request.counterpartyName());
            allHits.addAll(counterpartyBlacklistHits);
        }

        // 3. Sanctions screening — customer
        if (request.customerName() != null) {
            List<AmlHit> sanctionHits = sanctionScreeningService.screen(
                    request.customerName(),
                    request.customerDateOfBirth(),
                    request.customerNationality(),
                    request.customerIdNumber(),
                    null);
            allHits.addAll(sanctionHits);
        }

        // 4. Sanctions screening — counterparty
        if (request.counterpartyName() != null) {
            List<AmlHit> counterpartySanctionHits = sanctionScreeningService.screenCounterparty(
                    request.counterpartyName(), request.counterpartyCountry());
            allHits.addAll(counterpartySanctionHits);
        }

        // 5. Country restriction checks
        if (request.counterpartyCountry() != null) {
            List<AmlHit> countryHits = restrictionCheckService.checkCountryRisk(request.counterpartyCountry());
            allHits.addAll(countryHits);
        }

        // Compute risk score and restriction intent
        int riskScore = computeRiskScore(allHits);
        String riskLevel = classifyRiskLevel(riskScore);
        RestrictionIntent intent = determineRestrictionIntent(allHits, riskScore);
        boolean manualReview = intent == RestrictionIntent.REVIEW || intent == RestrictionIntent.ESCALATE;

        // Persist evidence
        persistEvidence(evaluationId, request, allHits);

        log.info("AML evaluation complete: evaluationId={}, customerId={}, traceId={}, hits={}, riskScore={}, intent={}",
                evaluationId, request.customerId(), request.traceId(), allHits.size(), riskScore, intent);

        return new AmlEvaluationResponse(
                evaluationId,
                request.customerId(),
                request.traceId(),
                !allHits.isEmpty(),
                allHits,
                intent,
                manualReview,
                riskLevel,
                riskScore,
                evaluationId,
                Instant.now()
        );
    }

    private int computeRiskScore(List<AmlHit> hits) {
        if (hits.isEmpty()) return 0;

        int maxScore = 0;
        for (AmlHit hit : hits) {
            int weight = switch (hit.hitType()) {
                case SANCTION -> 95;
                case BLACKLIST_USER, BLACKLIST_COUNTERPARTY -> 90;
                case UNACCEPTABLE_COUNTRY -> 95;
                case PEP -> 80;
                case HIGH_RISK_COUNTRY -> 60;
                case ORG_REGISTRATION_RESTRICTED -> 70;
                case WATCHLIST -> 50;
                case ADVERSE_MEDIA -> 40;
            };
            maxScore = Math.max(maxScore, weight);
        }
        return maxScore;
    }

    private String classifyRiskLevel(int score) {
        if (score >= 90) return "HIGH";
        if (score >= 70) return "MEDIUM";
        if (score > 0) return "LOW";
        return "NONE";
    }

    private RestrictionIntent determineRestrictionIntent(List<AmlHit> hits, int riskScore) {
        if (hits.isEmpty()) return RestrictionIntent.NONE;

        boolean hasSanction = hits.stream().anyMatch(h -> h.hitType() == HitType.SANCTION);
        boolean hasBlacklist = hits.stream().anyMatch(h ->
                h.hitType() == HitType.BLACKLIST_USER || h.hitType() == HitType.BLACKLIST_COUNTERPARTY);
        boolean hasUnacceptable = hits.stream().anyMatch(h -> h.hitType() == HitType.UNACCEPTABLE_COUNTRY);

        if (hasSanction || hasBlacklist || hasUnacceptable) return RestrictionIntent.BLOCK;
        if (riskScore >= 80) return RestrictionIntent.ESCALATE;
        if (riskScore >= 60) return RestrictionIntent.REVIEW;
        return RestrictionIntent.ALLOW;
    }

    private void persistEvidence(String evaluationId, AmlEvaluationRequest request, List<AmlHit> hits) {
        if (hits.isEmpty()) {
            AmlEvidenceRecord noHit = new AmlEvidenceRecord();
            noHit.setId("ev_" + compactUuid());
            noHit.setEvaluationId(evaluationId);
            noHit.setCustomerId(request.customerId());
            noHit.setTraceId(request.traceId());
            noHit.setCheckType("FULL_EVALUATION");
            noHit.setHit(false);
            noHit.setActor(request.actor());
            noHit.setSourceSystem(request.sourceSystem());
            evidenceRepository.save(noHit);
            return;
        }

        for (AmlHit hit : hits) {
            AmlEvidenceRecord record = new AmlEvidenceRecord();
            record.setId("ev_" + compactUuid());
            record.setEvaluationId(evaluationId);
            record.setCustomerId(request.customerId());
            record.setTraceId(request.traceId());
            record.setCheckType(hit.hitType().name());
            record.setHit(true);
            record.setReasonCode(hit.reasonCode());
            record.setHitType(hit.hitType().name());
            record.setEntityId(hit.entityId());
            record.setEntityName(hit.entityName());
            record.setProvider(hit.provider());
            record.setMatchScore(hit.matchScore());
            record.setDetailJson(hit.detail());
            record.setActor(request.actor());
            record.setSourceSystem(request.sourceSystem());
            evidenceRepository.save(record);
        }
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
