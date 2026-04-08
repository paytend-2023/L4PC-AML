package com.paytend.l4pc.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "watchlist_entity")
public class WatchlistEntityDO {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "entity_type", nullable = false, length = 16)
    private String entityType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(name = "profile_notes", columnDefinition = "TEXT")
    private String profileNotes;

    @Column(name = "source_list", length = 256)
    private String sourceList;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getProfileNotes() { return profileNotes; }
    public void setProfileNotes(String profileNotes) { this.profileNotes = profileNotes; }
    public String getSourceList() { return sourceList; }
    public void setSourceList(String sourceList) { this.sourceList = sourceList; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
