package com.paytend.l4pc.aml.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "data_sync_record")
public class DataSyncRecordEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "sync_type", nullable = false, length = 16)
    private String syncType;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "records_processed", nullable = false)
    private int recordsProcessed;

    @Column(name = "records_added", nullable = false)
    private int recordsAdded;

    @Column(name = "records_updated", nullable = false)
    private int recordsUpdated;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRecordsProcessed() { return recordsProcessed; }
    public void setRecordsProcessed(int recordsProcessed) { this.recordsProcessed = recordsProcessed; }
    public int getRecordsAdded() { return recordsAdded; }
    public void setRecordsAdded(int recordsAdded) { this.recordsAdded = recordsAdded; }
    public int getRecordsUpdated() { return recordsUpdated; }
    public void setRecordsUpdated(int recordsUpdated) { this.recordsUpdated = recordsUpdated; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
