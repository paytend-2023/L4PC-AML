package com.paytend.l4pc.aml.service.dataimport;

import com.paytend.l4pc.aml.domain.*;
import com.paytend.l4pc.aml.service.matching.NameMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Imports UN Security Council consolidated sanctions list.
 * Source: https://scsanctions.un.org/resources/xml/en/consolidated.xml
 */
@Component
@ConditionalOnProperty(name = "aml.un-sanctions.enabled", havingValue = "true")
public class UnSanctionsImporter {

    private static final Logger log = LoggerFactory.getLogger(UnSanctionsImporter.class);
    private static final String PROVIDER = "UN_SANCTIONS";
    private static final String UN_SANCTIONS_URL =
            "https://scsanctions.un.org/resources/xml/en/consolidated.xml";

    private final WatchlistEntityRepository entityRepository;
    private final WatchlistNameRepository nameRepository;
    private final WatchlistIdentityRepository identityRepository;
    private final DataSyncRecordRepository syncRecordRepository;
    private final NameMatcher nameMatcher;
    private final HttpClient httpClient;

    public UnSanctionsImporter(WatchlistEntityRepository entityRepository,
                               WatchlistNameRepository nameRepository,
                               WatchlistIdentityRepository identityRepository,
                               DataSyncRecordRepository syncRecordRepository,
                               NameMatcher nameMatcher) {
        this.entityRepository = entityRepository;
        this.nameRepository = nameRepository;
        this.identityRepository = identityRepository;
        this.syncRecordRepository = syncRecordRepository;
        this.nameMatcher = nameMatcher;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Scheduled(cron = "${aml.un-sanctions.cron:0 0 5 * * ?}")
    @Transactional
    public void importSanctions() {
        String recordId = "ds_" + UUID.randomUUID().toString().replace("-", "");
        Instant startedAt = Instant.now();

        DataSyncRecordEntity record = new DataSyncRecordEntity();
        record.setId(recordId);
        record.setProvider(PROVIDER);
        record.setSyncType("FULL");
        record.setFileName("consolidated.xml");
        record.setStatus("RUNNING");
        record.setStartedAt(startedAt);
        syncRecordRepository.save(record);

        try {
            int processed = fetchAndParse();
            record.setRecordsProcessed(processed);
            record.setRecordsAdded(processed);
            record.setStatus("SUCCESS");
            record.setCompletedAt(Instant.now());
            syncRecordRepository.save(record);
            log.info("UN sanctions import completed: {} entities", processed);
        } catch (Exception e) {
            log.error("UN sanctions import failed: {}", e.getMessage(), e);
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            record.setCompletedAt(Instant.now());
            syncRecordRepository.save(record);
        }
    }

    int fetchAndParse() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UN_SANCTIONS_URL))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("UN sanctions API returned status " + response.statusCode());
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(response.body());
        doc.getDocumentElement().normalize();

        // UN XML structure: <CONSOLIDATED_LIST><INDIVIDUALS><INDIVIDUAL>...</INDIVIDUAL></INDIVIDUALS>
        //                   <ENTITIES><ENTITY>...</ENTITY></ENTITIES></CONSOLIDATED_LIST>
        int processed = 0;

        List<WatchlistEntityDO> entityBatch = new ArrayList<>();
        List<WatchlistNameDO> nameBatch = new ArrayList<>();
        List<WatchlistIdentityDO> identityBatch = new ArrayList<>();

        // Process individuals
        NodeList individuals = doc.getElementsByTagName("INDIVIDUAL");
        for (int i = 0; i < individuals.getLength(); i++) {
            try {
                processUnIndividual((Element) individuals.item(i), entityBatch, nameBatch, identityBatch);
                processed++;
                if (entityBatch.size() >= 500) {
                    flush(entityBatch, nameBatch, identityBatch);
                }
            } catch (Exception e) {
                log.warn("Failed to process UN individual at index {}: {}", i, e.getMessage());
            }
        }

        // Process entities
        NodeList entities = doc.getElementsByTagName("ENTITY");
        for (int i = 0; i < entities.getLength(); i++) {
            try {
                processUnEntity((Element) entities.item(i), entityBatch, nameBatch, identityBatch);
                processed++;
                if (entityBatch.size() >= 500) {
                    flush(entityBatch, nameBatch, identityBatch);
                }
            } catch (Exception e) {
                log.warn("Failed to process UN entity at index {}: {}", i, e.getMessage());
            }
        }

        if (!entityBatch.isEmpty()) {
            flush(entityBatch, nameBatch, identityBatch);
        }
        return processed;
    }

    private void processUnIndividual(Element el,
                                     List<WatchlistEntityDO> entityBatch,
                                     List<WatchlistNameDO> nameBatch,
                                     List<WatchlistIdentityDO> identityBatch) {
        String dataid = getChildText(el, "DATAID");
        String externalId = dataid != null ? dataid : String.valueOf(el.hashCode());
        Instant now = Instant.now();

        Optional<WatchlistEntityDO> existing = entityRepository.findByProviderAndExternalId(PROVIDER, externalId);
        WatchlistEntityDO entity = existing.orElseGet(WatchlistEntityDO::new);
        if (entity.getId() == null) entity.setId("we_" + compactUuid());
        entity.setProvider(PROVIDER);
        entity.setExternalId(externalId);
        entity.setEntityType("PERSON");
        entity.setStatus("ACTIVE");
        entity.setCategory("SANCTION");
        entity.setSourceList("UN Security Council Consolidated List");
        entity.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt() : now);
        entity.setUpdatedAt(now);

        String comments = getChildText(el, "COMMENTS1");
        if (comments != null) entity.setProfileNotes(comments);
        entityBatch.add(entity);

        if (existing.isPresent()) {
            nameRepository.deleteByEntityId(entity.getId());
            identityRepository.deleteByEntityId(entity.getId());
        }

        // Primary name
        String firstName = getChildText(el, "FIRST_NAME");
        String secondName = getChildText(el, "SECOND_NAME");
        String thirdName = getChildText(el, "THIRD_NAME");

        addName(entity.getId(), "PRIMARY", firstName, secondName, thirdName, null, nameBatch, now);

        // Aliases
        NodeList aliases = el.getElementsByTagName("INDIVIDUAL_ALIAS");
        for (int i = 0; i < aliases.getLength(); i++) {
            Element alias = (Element) aliases.item(i);
            String aliasName = getChildText(alias, "ALIAS_NAME");
            if (aliasName != null) {
                addName(entity.getId(), "ALIAS", null, null, null, aliasName, nameBatch, now);
            }
        }

        // DOB
        NodeList dobs = el.getElementsByTagName("INDIVIDUAL_DATE_OF_BIRTH");
        for (int i = 0; i < dobs.getLength(); i++) {
            Element dobEl = (Element) dobs.item(i);
            String date = getChildText(dobEl, "DATE");
            if (date == null) date = getChildText(dobEl, "YEAR");
            if (date != null) {
                WatchlistIdentityDO identity = new WatchlistIdentityDO();
                identity.setId("wi_" + compactUuid());
                identity.setEntityId(entity.getId());
                identity.setDateOfBirth(date);
                identity.setCreatedAt(now);
                identityBatch.add(identity);
            }
        }

        // Nationality
        String nationality = getChildText(el, "NATIONALITY");
        if (nationality != null) {
            WatchlistIdentityDO identity = new WatchlistIdentityDO();
            identity.setId("wi_" + compactUuid());
            identity.setEntityId(entity.getId());
            identity.setNationality(nationality.length() > 2 ? nationality.substring(0, 2) : nationality);
            identity.setCreatedAt(now);
            identityBatch.add(identity);
        }

        // Documents (passports etc.)
        NodeList docs = el.getElementsByTagName("INDIVIDUAL_DOCUMENT");
        for (int i = 0; i < docs.getLength(); i++) {
            Element docEl = (Element) docs.item(i);
            String docType = getChildText(docEl, "TYPE_OF_DOCUMENT");
            String docNum = getChildText(docEl, "NUMBER");
            String country = getChildText(docEl, "ISSUING_COUNTRY");
            if (docNum != null) {
                WatchlistIdentityDO identity = new WatchlistIdentityDO();
                identity.setId("wi_" + compactUuid());
                identity.setEntityId(entity.getId());
                identity.setIdType(docType);
                identity.setIdNumber(docNum);
                identity.setCountryOfIssue(country);
                identity.setCreatedAt(now);
                identityBatch.add(identity);
            }
        }
    }

    private void processUnEntity(Element el,
                                 List<WatchlistEntityDO> entityBatch,
                                 List<WatchlistNameDO> nameBatch,
                                 List<WatchlistIdentityDO> identityBatch) {
        String dataid = getChildText(el, "DATAID");
        String externalId = dataid != null ? dataid : String.valueOf(el.hashCode());
        Instant now = Instant.now();

        Optional<WatchlistEntityDO> existing = entityRepository.findByProviderAndExternalId(PROVIDER, externalId);
        WatchlistEntityDO entity = existing.orElseGet(WatchlistEntityDO::new);
        if (entity.getId() == null) entity.setId("we_" + compactUuid());
        entity.setProvider(PROVIDER);
        entity.setExternalId(externalId);
        entity.setEntityType("ENTITY");
        entity.setStatus("ACTIVE");
        entity.setCategory("SANCTION");
        entity.setSourceList("UN Security Council Consolidated List");
        entity.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entityBatch.add(entity);

        if (existing.isPresent()) {
            nameRepository.deleteByEntityId(entity.getId());
        }

        String firstName = getChildText(el, "FIRST_NAME");
        addName(entity.getId(), "PRIMARY", null, null, null, firstName, nameBatch, now);

        NodeList aliases = el.getElementsByTagName("ENTITY_ALIAS");
        for (int i = 0; i < aliases.getLength(); i++) {
            Element alias = (Element) aliases.item(i);
            String aliasName = getChildText(alias, "ALIAS_NAME");
            if (aliasName != null) {
                addName(entity.getId(), "ALIAS", null, null, null, aliasName, nameBatch, now);
            }
        }
    }

    private void addName(String entityId, String nameType,
                         String firstName, String surname, String middleName,
                         String fullNameOverride,
                         List<WatchlistNameDO> batch, Instant now) {
        WatchlistNameDO name = new WatchlistNameDO();
        name.setId("wn_" + compactUuid());
        name.setEntityId(entityId);
        name.setNameType(nameType);
        name.setFirstName(firstName);
        name.setSurname(surname);
        name.setMiddleName(middleName);
        name.setCreatedAt(now);

        String fullName = fullNameOverride;
        if (fullName == null || fullName.isBlank()) {
            StringBuilder sb = new StringBuilder();
            if (firstName != null) sb.append(firstName);
            if (middleName != null) { if (!sb.isEmpty()) sb.append(" "); sb.append(middleName); }
            if (surname != null) { if (!sb.isEmpty()) sb.append(" "); sb.append(surname); }
            fullName = sb.toString();
        }
        name.setFullName(fullName);

        if (!fullName.isBlank()) {
            String normalized = nameMatcher.normalize(fullName);
            name.setNormalizedName(normalized);
            name.setSoundexCode(nameMatcher.soundex(normalized));
        }
        batch.add(name);
    }

    private void flush(List<WatchlistEntityDO> entities,
                       List<WatchlistNameDO> names,
                       List<WatchlistIdentityDO> identities) {
        entityRepository.saveAll(entities);
        nameRepository.saveAll(names);
        identityRepository.saveAll(identities);
        entities.clear();
        names.clear();
        identities.clear();
    }

    private String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
