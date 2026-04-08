package com.paytend.l4pc.aml.service.dataimport;

import com.paytend.l4pc.aml.domain.*;
import com.paytend.l4pc.aml.service.matching.NameMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Parses Dow Jones PFA XML files and writes entities to the watchlist tables.
 * Supports both Person and Entity record types.
 *
 * XML structure:
 * <PFA>
 *   <Records>
 *     <Person id="123" action="add" date="2024-01-01">
 *       <Gender>Male</Gender>
 *       <ActiveStatus>Active</ActiveStatus>
 *       <NameDetails><Name NameType="Primary">...</Name></NameDetails>
 *       <DateDetails><DateType DateTypeID="...">DOB</DateType>...</DateDetails>
 *       <CountryDetails>...</CountryDetails>
 *       <IDNumberTypes>...</IDNumberTypes>
 *       ...
 *     </Person>
 *     <Entity id="456" action="add" date="2024-01-01">...</Entity>
 *   </Records>
 * </PFA>
 */
@Component
public class DowJonesXmlParser {

    private static final Logger log = LoggerFactory.getLogger(DowJonesXmlParser.class);
    private static final int BATCH_SIZE = 500;

    private final WatchlistEntityRepository entityRepository;
    private final WatchlistNameRepository nameRepository;
    private final WatchlistIdentityRepository identityRepository;
    private final NameMatcher nameMatcher;

    public DowJonesXmlParser(WatchlistEntityRepository entityRepository,
                             WatchlistNameRepository nameRepository,
                             WatchlistIdentityRepository identityRepository,
                             NameMatcher nameMatcher) {
        this.entityRepository = entityRepository;
        this.nameRepository = nameRepository;
        this.identityRepository = identityRepository;
        this.nameMatcher = nameMatcher;
    }

    /**
     * Parse a single PFA XML file and upsert all records into watchlist tables.
     * Returns the number of records processed.
     */
    public int parseFile(File xmlFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Prevent XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        int processed = 0;
        Element root = doc.getDocumentElement();

        // Process Records section
        NodeList records = root.getElementsByTagName("Records");
        if (records.getLength() > 0) {
            Element recordsElement = (Element) records.item(0);

            // Process Person records
            NodeList persons = recordsElement.getElementsByTagName("Person");
            processed += processRecords(persons, "PERSON");

            // Process Entity records
            NodeList entities = recordsElement.getElementsByTagName("Entity");
            processed += processRecords(entities, "ENTITY");
        }

        log.info("Parsed {}: {} records processed", xmlFile.getName(), processed);
        return processed;
    }

    private int processRecords(NodeList nodes, String entityType) {
        int count = 0;
        List<WatchlistEntityDO> entityBatch = new ArrayList<>();
        List<WatchlistNameDO> nameBatch = new ArrayList<>();
        List<WatchlistIdentityDO> identityBatch = new ArrayList<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            Element record = (Element) nodes.item(i);
            try {
                processRecord(record, entityType, entityBatch, nameBatch, identityBatch);
                count++;

                if (entityBatch.size() >= BATCH_SIZE) {
                    flushBatch(entityBatch, nameBatch, identityBatch);
                }
            } catch (Exception e) {
                String id = record.getAttribute("id");
                log.warn("Failed to process {} record {}: {}", entityType, id, e.getMessage());
            }
        }

        if (!entityBatch.isEmpty()) {
            flushBatch(entityBatch, nameBatch, identityBatch);
        }
        return count;
    }

    private void processRecord(Element record, String entityType,
                               List<WatchlistEntityDO> entityBatch,
                               List<WatchlistNameDO> nameBatch,
                               List<WatchlistIdentityDO> identityBatch) {
        String externalId = record.getAttribute("id");
        String action = record.getAttribute("action");
        Instant now = Instant.now();

        // Determine category from record content
        String category = determineCategory(record);

        // Check if entity already exists
        Optional<WatchlistEntityDO> existing = entityRepository.findByProviderAndExternalId("DOW_JONES", externalId);

        if ("del".equalsIgnoreCase(action) && existing.isPresent()) {
            // Mark as inactive on delete action
            WatchlistEntityDO entity = existing.get();
            entity.setStatus("INACTIVE");
            entity.setUpdatedAt(now);
            entityRepository.save(entity);
            return;
        }

        // Create or update entity
        WatchlistEntityDO entity = existing.orElseGet(WatchlistEntityDO::new);
        if (entity.getId() == null) {
            entity.setId("we_" + compactUuid());
        }
        entity.setProvider("DOW_JONES");
        entity.setExternalId(externalId);
        entity.setEntityType(entityType);
        entity.setStatus("ACTIVE");
        entity.setCategory(category);
        entity.setProfileNotes(getChildText(record, "ProfileNotes"));
        entity.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt() : now);
        entity.setUpdatedAt(now);

        // Determine source list from SanctionsReferences
        String sourceList = extractSourceList(record);
        if (sourceList != null) entity.setSourceList(sourceList);

        entityBatch.add(entity);

        // Process names (clear and re-insert for updates)
        if (existing.isPresent()) {
            nameRepository.deleteByEntityId(entity.getId());
            identityRepository.deleteByEntityId(entity.getId());
        }

        extractNames(record, entity.getId(), entityType, nameBatch, now);
        extractIdentities(record, entity.getId(), identityBatch, now);
    }

    private void extractNames(Element record, String entityId, String entityType,
                              List<WatchlistNameDO> batch, Instant now) {
        NodeList nameDetailsList = record.getElementsByTagName("NameDetails");
        if (nameDetailsList.getLength() == 0) return;

        Element nameDetails = (Element) nameDetailsList.item(0);
        NodeList names = nameDetails.getElementsByTagName("Name");

        for (int i = 0; i < names.getLength(); i++) {
            Element nameElement = (Element) names.item(i);
            String nameType = nameElement.getAttribute("NameType");

            WatchlistNameDO name = new WatchlistNameDO();
            name.setId("wn_" + compactUuid());
            name.setEntityId(entityId);
            name.setNameType(mapNameType(nameType));
            name.setCreatedAt(now);

            if ("ENTITY".equals(entityType)) {
                String entityName = getChildText(nameElement, "EntityName");
                name.setEntityName(entityName);
                name.setFullName(entityName);
            } else {
                String firstName = getChildText(nameElement, "FirstName");
                String surname = getChildText(nameElement, "Surname");
                String middleName = getChildText(nameElement, "MiddleName");
                name.setFirstName(firstName);
                name.setSurname(surname);
                name.setMiddleName(middleName);

                StringBuilder fullName = new StringBuilder();
                if (firstName != null) fullName.append(firstName);
                if (middleName != null) {
                    if (!fullName.isEmpty()) fullName.append(" ");
                    fullName.append(middleName);
                }
                if (surname != null) {
                    if (!fullName.isEmpty()) fullName.append(" ");
                    fullName.append(surname);
                }
                name.setFullName(fullName.toString());
            }

            // Pre-compute normalized name and Soundex for search indexing
            if (name.getFullName() != null && !name.getFullName().isBlank()) {
                String normalized = nameMatcher.normalize(name.getFullName());
                name.setNormalizedName(normalized);
                name.setSoundexCode(nameMatcher.soundex(normalized));
            }

            batch.add(name);
        }
    }

    private void extractIdentities(Element record, String entityId,
                                   List<WatchlistIdentityDO> batch, Instant now) {
        // Date of birth
        NodeList dateDetailsList = record.getElementsByTagName("DateDetails");
        if (dateDetailsList.getLength() > 0) {
            Element dateDetails = (Element) dateDetailsList.item(0);
            NodeList dateTypes = dateDetails.getElementsByTagName("DateType");
            for (int i = 0; i < dateTypes.getLength(); i++) {
                Element dateType = (Element) dateTypes.item(i);
                String typeText = dateType.getTextContent().trim();
                if ("Date of Birth".equalsIgnoreCase(typeText) || "DOB".equalsIgnoreCase(typeText)) {
                    String dateValue = getNextSiblingText(dateType, "DateValue");
                    if (dateValue != null) {
                        WatchlistIdentityDO identity = new WatchlistIdentityDO();
                        identity.setId("wi_" + compactUuid());
                        identity.setEntityId(entityId);
                        identity.setDateOfBirth(dateValue);
                        identity.setCreatedAt(now);
                        batch.add(identity);
                    }
                }
            }
        }

        // Country / Nationality
        NodeList countryDetailsList = record.getElementsByTagName("CountryDetails");
        if (countryDetailsList.getLength() > 0) {
            Element countryDetails = (Element) countryDetailsList.item(0);
            NodeList countryTypes = countryDetails.getElementsByTagName("CountryType");
            for (int i = 0; i < countryTypes.getLength(); i++) {
                Element countryType = (Element) countryTypes.item(i);
                String type = countryType.getTextContent().trim();
                if ("Citizenship".equalsIgnoreCase(type) || "Nationality".equalsIgnoreCase(type)) {
                    String code = getNextSiblingText(countryType, "Code");
                    if (code != null) {
                        WatchlistIdentityDO identity = new WatchlistIdentityDO();
                        identity.setId("wi_" + compactUuid());
                        identity.setEntityId(entityId);
                        identity.setNationality(code);
                        identity.setCreatedAt(now);
                        batch.add(identity);
                    }
                }
            }
        }

        // ID Numbers
        NodeList idNumbersList = record.getElementsByTagName("IDNumberTypes");
        if (idNumbersList.getLength() > 0) {
            Element idNumbers = (Element) idNumbersList.item(0);
            NodeList idTypes = idNumbers.getElementsByTagName("IDType");
            for (int i = 0; i < idTypes.getLength(); i++) {
                Element idTypeElement = (Element) idTypes.item(i);
                String idType = idTypeElement.getTextContent().trim();
                String idValue = getNextSiblingText(idTypeElement, "IDValue");
                if (idValue != null && !idValue.isBlank()) {
                    WatchlistIdentityDO identity = new WatchlistIdentityDO();
                    identity.setId("wi_" + compactUuid());
                    identity.setEntityId(entityId);
                    identity.setIdType(idType);
                    identity.setIdNumber(idValue);
                    identity.setCreatedAt(now);
                    batch.add(identity);
                }
            }
        }
    }

    private String determineCategory(Element record) {
        // Check SanctionsReferences to determine if this is a sanction entry
        NodeList sanctionsRefs = record.getElementsByTagName("SanctionsReferences");
        if (sanctionsRefs.getLength() > 0) {
            return "SANCTION";
        }
        // Check roles for PEP indicators
        NodeList roles = record.getElementsByTagName("RoleDetail");
        if (roles.getLength() > 0) {
            for (int i = 0; i < roles.getLength(); i++) {
                String roleText = roles.item(i).getTextContent().toLowerCase();
                if (roleText.contains("pep") || roleText.contains("political")
                        || roleText.contains("government") || roleText.contains("minister")) {
                    return "PEP";
                }
            }
        }
        return "WATCHLIST";
    }

    private String extractSourceList(Element record) {
        NodeList refs = record.getElementsByTagName("SanctionsReference");
        if (refs.getLength() == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < refs.getLength() && i < 5; i++) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(refs.item(i).getTextContent().trim());
        }
        return sb.length() > 256 ? sb.substring(0, 256) : sb.toString();
    }

    private void flushBatch(List<WatchlistEntityDO> entities,
                            List<WatchlistNameDO> names,
                            List<WatchlistIdentityDO> identities) {
        entityRepository.saveAll(entities);
        nameRepository.saveAll(names);
        identityRepository.saveAll(identities);
        entities.clear();
        names.clear();
        identities.clear();
    }

    // --- XML helpers ---

    private String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    private String getNextSiblingText(Element element, String siblingTag) {
        Node sibling = element.getNextSibling();
        while (sibling != null) {
            if (sibling instanceof Element el && siblingTag.equals(el.getTagName())) {
                String text = el.getTextContent();
                return text != null && !text.isBlank() ? text.trim() : null;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    private String mapNameType(String djNameType) {
        if (djNameType == null) return "PRIMARY";
        return switch (djNameType.toLowerCase()) {
            case "primary name", "primary" -> "PRIMARY";
            case "also known as", "aka", "alias" -> "ALIAS";
            case "maiden name" -> "MAIDEN";
            case "formerly known as", "former" -> "FORMER";
            default -> "PRIMARY";
        };
    }

    private static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
