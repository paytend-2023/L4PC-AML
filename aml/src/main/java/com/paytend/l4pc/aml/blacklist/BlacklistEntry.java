package com.paytend.l4pc.aml.blacklist;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Blacklist entry — covers user blacklist and counterparty (transaction) blacklist.
 *
 * Migrated from:
 * - BlacklistService.groovy (user blacklist: mobile, idno, passport, IP, account, bank)
 * - TransactionBlacklistService.groovy (external account blacklist)
 *
 * PC-AML does NOT execute restrictions — it only reports hits.
 */
@Entity
@Table(name = "blacklist_entry", indexes = {
        @Index(name = "idx_bl_identifier", columnList = "identifierType,identifierValue"),
        @Index(name = "idx_bl_status", columnList = "status")
})
public class BlacklistEntry {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private BlacklistType identifierType;

    @Column(nullable = false, length = 200)
    private String identifierValue;

    @Column(length = 200)
    private String entityName;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant disabledAt;

    @Column(length = 100)
    private String disabledBy;

    public BlacklistEntry() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public BlacklistType getIdentifierType() { return identifierType; }
    public void setIdentifierType(BlacklistType identifierType) { this.identifierType = identifierType; }
    public String getIdentifierValue() { return identifierValue; }
    public void setIdentifierValue(String identifierValue) { this.identifierValue = identifierValue; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getDisabledAt() { return disabledAt; }
    public void setDisabledAt(Instant disabledAt) { this.disabledAt = disabledAt; }
    public String getDisabledBy() { return disabledBy; }
    public void setDisabledBy(String disabledBy) { this.disabledBy = disabledBy; }
}
