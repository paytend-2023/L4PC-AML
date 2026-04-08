package com.paytend.l4pc.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "screening_match")
public class ScreeningMatchEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "screening_id", nullable = false, length = 64)
    private String screeningId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @Column(name = "match_type", nullable = false, length = 32)
    private String matchType;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(name = "entity_id", length = 128)
    private String entityId;

    @Column(name = "entity_name", length = 512)
    private String entityName;

    @Column(name = "entity_type", length = 16)
    private String entityType;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getScreeningId() { return screeningId; }
    public void setScreeningId(String screeningId) { this.screeningId = screeningId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
