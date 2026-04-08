package com.paytend.l4pc.aml.restriction;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Risk country configuration.
 *
 * Migrated from: RiskCountryService.groovy
 *
 * Categories:
 * - UNACCEPTABLE: transaction completely blocked
 * - HIGH_RISK: triggers manual review
 * - RESTRICTED_REGISTRATION: registration blocked for this country
 *
 * PC-AML outputs restriction_intent; L3 decides execution.
 */
@Entity
@Table(name = "risk_country", indexes = {
        @Index(name = "idx_rc_country_type", columnList = "countryCode,restrictionType"),
        @Index(name = "idx_rc_status", columnList = "status")
})
public class RiskCountryEntry {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(length = 100)
    private String countryName;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private RestrictionType restrictionType;

    @Column(length = 30)
    private String scope;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(length = 500)
    private String reason;

    @Column(length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RiskCountryEntry() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public RestrictionType getRestrictionType() { return restrictionType; }
    public void setRestrictionType(RestrictionType restrictionType) { this.restrictionType = restrictionType; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
