package com.paytend.l4pc.aml.evidence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Immutable evidence record for every AML evaluation.
 * Fulfills the evidence chain requirement from the 7-layer model.
 *
 * Evidence must be independently preserved for:
 * - Audit trail
 * - Replay / determinism
 * - Regulatory reporting
 */
@Entity
@Table(name = "aml_evidence", indexes = {
        @Index(name = "idx_ev_eval_id", columnList = "evaluationId"),
        @Index(name = "idx_ev_customer", columnList = "customerId"),
        @Index(name = "idx_ev_trace", columnList = "traceId")
})
public class AmlEvidenceRecord {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 40)
    private String evaluationId;

    @Column(nullable = false, length = 80)
    private String customerId;

    @Column(nullable = false, length = 80)
    private String traceId;

    @Column(nullable = false, length = 30)
    private String checkType;

    @Column(nullable = false)
    private boolean hit;

    @Column(length = 60)
    private String reasonCode;

    @Column(length = 30)
    private String hitType;

    @Column(length = 200)
    private String entityId;

    @Column(length = 200)
    private String entityName;

    @Column(length = 60)
    private String provider;

    private int matchScore;

    @Column(columnDefinition = "TEXT")
    private String detailJson;

    @Column(length = 100)
    private String actor;

    @Column(length = 100)
    private String sourceSystem;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AmlEvidenceRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEvaluationId() { return evaluationId; }
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public boolean isHit() { return hit; }
    public void setHit(boolean hit) { this.hit = hit; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getHitType() { return hitType; }
    public void setHitType(String hitType) { this.hitType = hitType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
