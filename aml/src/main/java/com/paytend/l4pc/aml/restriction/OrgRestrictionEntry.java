package com.paytend.l4pc.aml.restriction;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Organization registration restriction.
 *
 * Migrated from: OrgRegistrationRestrictionService.groovy
 *
 * Restricts org registration by nationality, residence country, or registration country.
 * PC-AML only reports the restriction — does NOT block registration.
 */
@Entity
@Table(name = "org_restriction", indexes = {
        @Index(name = "idx_or_country_field", columnList = "countryCode,restrictionField")
})
public class OrgRestrictionEntry {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 2)
    private String countryCode;

    /** NATIONALITY, RESIDENCE_COUNTRY, REGISTRATION_COUNTRY */
    @Column(nullable = false, length = 30)
    private String restrictionField;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(length = 500)
    private String reason;

    @Column(length = 100)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public OrgRestrictionEntry() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getRestrictionField() { return restrictionField; }
    public void setRestrictionField(String restrictionField) { this.restrictionField = restrictionField; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
