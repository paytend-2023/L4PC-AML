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
 * Imports EU consolidated sanctions list from the official XML endpoint.
 * Source: EU Financial Sanctions database (CFSP).
 * Updates watchlist_entity/name/identity tables with SANCTION category.
 */
@Component
@ConditionalOnProperty(name = "aml.eu-sanctions.enabled", havingValue = "true")
public class EuSanctionsImporter {

    private static final Logger log = LoggerFactory.getLogger(EuSanctionsImporter.class);
    private static final String PROVIDER = "EU_SANCTIONS";
    private static final String EU_SANCTIONS_URL =
            "https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw";

    private final WatchlistEntityRepository entityRepository;
    private final WatchlistNameRepository nameRepository;
    private final WatchlistIdentityRepository identityRepository;
    private final DataSyncRecordRepository syncRecordRepository;
    private final NameMatcher nameMatcher;
    private final HttpClient httpClient;

    public EuSanctionsImporter(WatchlistEntityRepository entityRepository,
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

    @Scheduled(cron = "${aml.eu-sanctions.cron:0 0 4 * * ?}")
    @Transactional
    public void importSanctions() {
        String recordId = "ds_" + UUID.randomUUID().toString().replace("-", "");
        Instant startedAt = Instant.now();

        DataSyncRecordEntity record = new DataSyncRecordEntity();
        record.setId(recordId);
        record.setProvider(PROVIDER);
        record.setSyncType("FULL");
        record.setFileName("xmlFullSanctionsList_1_1.xml");
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
            log.info("EU sanctions import completed: {} entities", processed);
        } catch (Exception e) {
            log.error("EU sanctions import failed: {}", e.getMessage(), e);
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
            record.setCompletedAt(Instant.now());
            syncRecordRepository.save(record);
        }
    }

    int fetchAndParse() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EU_SANCTIONS_URL))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("EU sanctions API returned status " + response.statusCode());
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(response.body());
        doc.getDocumentElement().normalize();

        // EU XML structure: <export><sanctionEntity>...</sanctionEntity></export>
        NodeList entities = doc.getElementsByTagName("sanctionEntity");
        int processed = 0;

        List<WatchlistEntityDO> entityBatch = new ArrayList<>();
        List<WatchlistNameDO> nameBatch = new ArrayList<>();
        List<WatchlistIdentityDO> identityBatch = new ArrayList<>();

        for (int i = 0; i < entities.getLength(); i++) {
            Element entityEl = (Element) entities.item(i);
            try {
                processEuEntity(entityEl, entityBatch, nameBatch, identityBatch);
                processed++;

                if (entityBatch.size() >= 500) {
                    entityRepository.saveAll(entityBatch);
                    nameRepository.saveAll(nameBatch);
                    identityRepository.saveAll(identityBatch);
                    entityBatch.clear();
                    nameBatch.clear();
                    identityBatch.clear();
                }
            } catch (Exception e) {
                log.warn("Failed to process EU entity at index {}: {}", i, e.getMessage());
            }
        }

        if (!entityBatch.isEmpty()) {
            entityRepository.saveAll(entityBatch);
            nameRepository.saveAll(nameBatch);
            identityRepository.saveAll(identityBatch);
        }

        return processed;
    }

    private void processEuEntity(Element entityEl,
                                 List<WatchlistEntityDO> entityBatch,
                                 List<WatchlistNameDO> nameBatch,
                                 List<WatchlistIdentityDO> identityBatch) {
        String logicalId = getAttr(entityEl, "logicalId");
        if (logicalId == null) logicalId = getAttr(entityEl, "designationId");
        String externalId = logicalId != null ? logicalId : String.valueOf(entityEl.hashCode());

        Instant now = Instant.now();

        // Determine entity type
        String subjectType = getChildText(entityEl, "subjectType");
        String entityType = "person".equalsIgnoreCase(subjectType) ? "PERSON" : "ENTITY";

        Optional<WatchlistEntityDO> existing = entityRepository.findByProviderAndExternalId(PROVIDER, externalId);
        WatchlistEntityDO entity = existing.orElseGet(WatchlistEntityDO::new);
        if (entity.getId() == null) {
            entity.setId("we_" + UUID.randomUUID().toString().replace("-", ""));
        }
        entity.setProvider(PROVIDER);
        entity.setExternalId(externalId);
        entity.setEntityType(entityType);
        entity.setStatus("ACTIVE");
        entity.setCategory("SANCTION");
        entity.setSourceList("EU Consolidated Financial Sanctions");
        entity.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt() : now);
        entity.setUpdatedAt(now);

        String remark = getChildText(entityEl, "remark");
        if (remark != null) entity.setProfileNotes(remark);

        entityBatch.add(entity);

        // Clear old names/identities for update
        if (existing.isPresent()) {
            nameRepository.deleteByEntityId(entity.getId());
            identityRepository.deleteByEntityId(entity.getId());
        }

        // Names from <nameAlias> elements
        NodeList nameAliases = entityEl.getElementsByTagName("nameAlias");
        for (int i = 0; i < nameAliases.getLength(); i++) {
            Element nameAlias = (Element) nameAliases.item(i);
            String wholeName = getAttr(nameAlias, "wholeName");
            String firstName = getAttr(nameAlias, "firstName");
            String lastName = getAttr(nameAlias, "lastName");
            String middleName = getAttr(nameAlias, "middleName");
            boolean isStrong = "true".equalsIgnoreCase(getAttr(nameAlias, "strong"));

            WatchlistNameDO name = new WatchlistNameDO();
            name.setId("wn_" + UUID.randomUUID().toString().replace("-", ""));
            name.setEntityId(entity.getId());
            name.setNameType(isStrong ? "PRIMARY" : "ALIAS");
            name.setFirstName(firstName);
            name.setSurname(lastName);
            name.setMiddleName(middleName);
            name.setEntityName("ENTITY".equals(entityType) ? wholeName : null);
            name.setFullName(wholeName);
            name.setCreatedAt(now);

            if (wholeName != null && !wholeName.isBlank()) {
                String normalized = nameMatcher.normalize(wholeName);
                name.setNormalizedName(normalized);
                name.setSoundexCode(nameMatcher.soundex(normalized));
            }
            nameBatch.add(name);
        }

        // Identifications from <identification> elements
        NodeList identifications = entityEl.getElementsByTagName("identification");
        for (int i = 0; i < identifications.getLength(); i++) {
            Element idEl = (Element) identifications.item(i);
            String idNumber = getAttr(idEl, "number");
            String idType = getAttr(idEl, "identificationTypeDescription");
            String country = getAttr(idEl, "countryIso2Code");

            if (idNumber != null && !idNumber.isBlank()) {
                WatchlistIdentityDO identity = new WatchlistIdentityDO();
                identity.setId("wi_" + UUID.randomUUID().toString().replace("-", ""));
                identity.setEntityId(entity.getId());
                identity.setIdType(idType);
                identity.setIdNumber(idNumber);
                identity.setCountryOfIssue(country);
                identity.setCreatedAt(now);
                identityBatch.add(identity);
            }
        }

        // Birth date from <birthdate> elements
        NodeList birthdates = entityEl.getElementsByTagName("birthdate");
        for (int i = 0; i < birthdates.getLength(); i++) {
            Element bdEl = (Element) birthdates.item(i);
            String birthdate = getAttr(bdEl, "birthdate");
            if (birthdate == null) birthdate = getAttr(bdEl, "year");
            String birthCountry = getAttr(bdEl, "countryIso2Code");

            if (birthdate != null) {
                WatchlistIdentityDO identity = new WatchlistIdentityDO();
                identity.setId("wi_" + UUID.randomUUID().toString().replace("-", ""));
                identity.setEntityId(entity.getId());
                identity.setDateOfBirth(birthdate);
                identity.setNationality(birthCountry);
                identity.setCreatedAt(now);
                identityBatch.add(identity);
            }
        }

        // Citizenship from <citizenship> elements
        NodeList citizenships = entityEl.getElementsByTagName("citizenship");
        for (int i = 0; i < citizenships.getLength(); i++) {
            Element cEl = (Element) citizenships.item(i);
            String countryCode = getAttr(cEl, "countryIso2Code");
            if (countryCode != null) {
                WatchlistIdentityDO identity = new WatchlistIdentityDO();
                identity.setId("wi_" + UUID.randomUUID().toString().replace("-", ""));
                identity.setEntityId(entity.getId());
                identity.setNationality(countryCode);
                identity.setCreatedAt(now);
                identityBatch.add(identity);
            }
        }
    }

    private String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private String getAttr(Element el, String attrName) {
        String val = el.getAttribute(attrName);
        return val != null && !val.isBlank() ? val.trim() : null;
    }
}
