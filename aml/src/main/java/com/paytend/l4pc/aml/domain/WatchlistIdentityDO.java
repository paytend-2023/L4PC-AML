package com.paytend.l4pc.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "watchlist_identity")
public class WatchlistIdentityDO {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(name = "date_of_birth", length = 32)
    private String dateOfBirth;

    @Column(length = 8)
    private String nationality;

    @Column(name = "id_type", length = 64)
    private String idType;

    @Column(name = "id_number", length = 128)
    private String idNumber;

    @Column(name = "country_of_issue", length = 8)
    private String countryOfIssue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getCountryOfIssue() { return countryOfIssue; }
    public void setCountryOfIssue(String countryOfIssue) { this.countryOfIssue = countryOfIssue; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
