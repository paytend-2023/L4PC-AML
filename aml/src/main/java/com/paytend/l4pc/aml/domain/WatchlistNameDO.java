package com.paytend.l4pc.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "watchlist_name")
public class WatchlistNameDO {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(name = "name_type", nullable = false, length = 32)
    private String nameType;

    @Column(name = "full_name", length = 512)
    private String fullName;

    @Column(name = "first_name", length = 256)
    private String firstName;

    @Column(length = 256)
    private String surname;

    @Column(name = "middle_name", length = 256)
    private String middleName;

    @Column(name = "entity_name", length = 512)
    private String entityName;

    @Column(name = "normalized_name", length = 512)
    private String normalizedName;

    @Column(name = "soundex_code", length = 16)
    private String soundexCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getNameType() { return nameType; }
    public void setNameType(String nameType) { this.nameType = nameType; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getSoundexCode() { return soundexCode; }
    public void setSoundexCode(String soundexCode) { this.soundexCode = soundexCode; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
