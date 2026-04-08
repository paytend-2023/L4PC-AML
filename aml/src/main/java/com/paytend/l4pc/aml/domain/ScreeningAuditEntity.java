package com.paytend.l4pc.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "screening_audit")
public class ScreeningAuditEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "screening_type", nullable = false, length = 32)
    private String screeningType;

    @Column(name = "request_input", nullable = false, columnDefinition = "TEXT")
    private String requestInput;

    @Column(name = "provider_output", columnDefinition = "TEXT")
    private String providerOutput;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(length = 128)
    private String actor;

    @Column(name = "source_system", length = 64)
    private String sourceSystem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getScreeningType() { return screeningType; }
    public void setScreeningType(String screeningType) { this.screeningType = screeningType; }

    public String getRequestInput() { return requestInput; }
    public void setRequestInput(String requestInput) { this.requestInput = requestInput; }

    public String getProviderOutput() { return providerOutput; }
    public void setProviderOutput(String providerOutput) { this.providerOutput = providerOutput; }

    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
