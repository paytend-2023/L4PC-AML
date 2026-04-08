package com.paytend.l4pc.aml.service.dataimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Calendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads Dow Jones PFA XML data files via HTTP Basic Auth.
 * Files are downloaded as ZIP archives, extracted to local directories
 * for subsequent parsing by DowJonesXmlParser.
 */
@Component
@EnableConfigurationProperties(DowJonesConfig.class)
@ConditionalOnProperty(name = "aml.dowjones.enabled", havingValue = "true")
public class DowJonesFileDownloader {

    private static final Logger log = LoggerFactory.getLogger(DowJonesFileDownloader.class);
    private static final String AUTH_HEADER = "Authorization";

    private final DowJonesConfig config;
    private final HttpClient httpClient;

    public DowJonesFileDownloader(DowJonesConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Scheduled(cron = "${aml.dowjones.download-cron:0 0 18 * * ?}")
    public void downloadFiles() {
        log.info("DJ file download started");
        try {
            String fileList = fetchFileList();
            if (fileList == null || fileList.isBlank()) {
                log.warn("DJ API returned empty file list");
                return;
            }

            String yesterdayDate = yesterdayDateString();
            String[] files = fileList.split(",");
            int downloaded = 0;

            for (String fileName : files) {
                String trimmed = fileName.trim();
                if (trimmed.isEmpty() || !trimmed.contains(yesterdayDate)) continue;
                if (trimmed.endsWith("f.zip")) continue;

                boolean isFullSync = trimmed.contains("splits");
                String targetDir = isFullSync ? config.getFullDir() : config.getIncrementDir();

                try {
                    downloadAndExtract(trimmed, targetDir);
                    downloaded++;
                } catch (Exception e) {
                    log.error("Failed to download DJ file {}: {}", trimmed, e.getMessage(), e);
                }
            }
            log.info("DJ file download completed: {} files downloaded", downloaded);
        } catch (Exception e) {
            log.error("DJ file download failed: {}", e.getMessage(), e);
        }
    }

    String fetchFileList() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl()))
                .header(AUTH_HEADER, "Basic " + config.getCredentials())
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("DJ API returned status {}: {}", response.statusCode(), response.body());
            return null;
        }
        return response.body();
    }

    void downloadAndExtract(String fileName, String targetDir) throws IOException, InterruptedException {
        Path dir = Path.of(targetDir);
        Files.createDirectories(dir);

        String downloadUrl = config.getBaseUrl() + fileName;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header(AUTH_HEADER, "Basic " + config.getCredentials())
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        Path zipPath = dir.resolve(fileName);
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(zipPath));

        if (response.statusCode() != 200) {
            Files.deleteIfExists(zipPath);
            throw new IOException("Download failed with status " + response.statusCode());
        }

        log.info("Downloaded {} to {}", fileName, zipPath);
        unzip(zipPath, dir);
        Files.deleteIfExists(zipPath);
    }

    void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                // Prevent zip-slip
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                Files.createDirectories(entryPath.getParent());
                Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.info("Extracted {} to {}", zipFile.getFileName(), targetDir);
    }

    private String yesterdayDateString() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return new SimpleDateFormat("yyyyMMdd").format(cal.getTime());
    }
}
