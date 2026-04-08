package com.paytend.l4pc.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "screening_customer")
public class ScreeningCustomerEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "customer_id", nullable = false, length = 64, unique = true)
    private String customerId;

    @Column(name = "customer_name", nullable = false, length = 512)
    private String customerName;

    @Column(name = "date_of_birth", length = 32)
    private String dateOfBirth;

    @Column(length = 8)
    private String nationality;

    @Column(name = "id_number", length = 128)
    private String idNumber;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;

    @Column(name = "screening_frequency", nullable = false, length = 16)
    private String screeningFrequency;

    @Column(name = "last_screened_at")
    private Instant lastScreenedAt;

    @Column(name = "next_screening_at")
    private Instant nextScreenedAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getScreeningFrequency() { return screeningFrequency; }
    public void setScreeningFrequency(String screeningFrequency) { this.screeningFrequency = screeningFrequency; }
    public Instant getLastScreenedAt() { return lastScreenedAt; }
    public void setLastScreenedAt(Instant lastScreenedAt) { this.lastScreenedAt = lastScreenedAt; }
    public Instant getNextScreenedAt() { return nextScreenedAt; }
    public void setNextScreenedAt(Instant nextScreenedAt) { this.nextScreenedAt = nextScreenedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
