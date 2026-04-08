package com.paytend.l4pc.aml.service.dataimport;

import com.paytend.l4pc.aml.domain.DataSyncRecordEntity;
import com.paytend.l4pc.aml.domain.DataSyncRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

/**
 * Scheduled task that scans downloaded DJ XML files and triggers parsing.
 * Tracks each sync operation in data_sync_record for audit.
 */
@Component
@EnableConfigurationProperties(DowJonesConfig.class)
@ConditionalOnProperty(name = "aml.dowjones.enabled", havingValue = "true")
public class DowJonesSyncTask {

    private static final Logger log = LoggerFactory.getLogger(DowJonesSyncTask.class);

    private final DowJonesConfig config;
    private final DowJonesXmlParser parser;
    private final DataSyncRecordRepository syncRecordRepository;

    public DowJonesSyncTask(DowJonesConfig config,
                            DowJonesXmlParser parser,
                            DataSyncRecordRepository syncRecordRepository) {
        this.config = config;
        this.parser = parser;
        this.syncRecordRepository = syncRecordRepository;
    }

    @Scheduled(cron = "${aml.dowjones.increment-sync-cron:0 0 20 * * ?}")
    public void syncIncremental() {
        log.info("DJ incremental sync started");
        for (String path : config.getIncrementPaths()) {
            syncDirectory(path, "INCREMENTAL");
        }
        // Also scan default increment dir
        syncDirectory(config.getIncrementDir(), "INCREMENTAL");
    }

    @Scheduled(cron = "${aml.dowjones.full-sync-cron:0 0 0 ? * SUN}")
    public void syncFull() {
        log.info("DJ full sync started");
        for (String path : config.getFullPaths()) {
            syncDirectory(path, "FULL");
        }
        syncDirectory(config.getFullDir(), "FULL");
    }

    private void syncDirectory(String dirPath, String syncType) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.debug("Directory does not exist or is not a directory: {}", dirPath);
            return;
        }

        File[] xmlFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) {
            log.debug("No XML files found in {}", dirPath);
            return;
        }

        for (File xmlFile : xmlFiles) {
            syncFile(xmlFile, syncType);
        }
    }

    @Transactional
    void syncFile(File xmlFile, String syncType) {
        String recordId = "ds_" + UUID.randomUUID().toString().replace("-", "");
        Instant startedAt = Instant.now();

        DataSyncRecordEntity record = new DataSyncRecordEntity();
        record.setId(recordId);
        record.setProvider("DOW_JONES");
        record.setSyncType(syncType);
        record.setFileName(xmlFile.getName());
        record.setStatus("RUNNING");
        record.setStartedAt(startedAt);
        syncRecordRepository.save(record);

        try {
            int processed = parser.parseFile(xmlFile);

            record.setRecordsProcessed(processed);
            record.setRecordsAdded(processed);
            record.setStatus("SUCCESS");
            record.setCompletedAt(Instant.now());
            syncRecordRepository.save(record);

            log.info("DJ sync completed for {}: {} records", xmlFile.getName(), processed);
        } catch (Exception e) {
            log.error("DJ sync failed for {}: {}", xmlFile.getName(), e.getMessage(), e);
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            record.setCompletedAt(Instant.now());
            syncRecordRepository.save(record);
        }
    }
}
