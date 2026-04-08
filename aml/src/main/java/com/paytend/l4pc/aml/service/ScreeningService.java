package com.paytend.l4pc.aml.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytend.l4pc.aml.api.MatchResponse;
import com.paytend.l4pc.aml.api.ScreeningRequest;
import com.paytend.l4pc.aml.api.ScreeningResponse;
import com.paytend.l4pc.aml.domain.*;
import com.paytend.l4pc.aml.service.provider.ProviderMatch;
import com.paytend.l4pc.aml.service.provider.ScreeningInput;
import com.paytend.l4pc.aml.service.provider.ScreeningProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    // --- Screening types ---
    public static final String TYPE_INITIAL = "INITIAL";
    public static final String TYPE_ONGOING = "ONGOING";
    public static final String TYPE_EVENT_TRIGGERED = "EVENT_TRIGGERED";
    public static final String TYPE_MANUAL = "MANUAL";
    private static final Set<String> VALID_SCREENING_TYPES = Set.of(
            TYPE_INITIAL, TYPE_ONGOING, TYPE_EVENT_TRIGGERED, TYPE_MANUAL);

    // --- Status ---
    public static final String STATUS_CLEAR = "CLEAR";
    public static final String STATUS_ALERT = "ALERT";
    public static final String STATUS_ERROR = "ERROR";

    // --- Risk levels ---
    public static final String RISK_HIGH = "HIGH";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_LOW = "LOW";

    // --- Thresholds (AML spec §4.3) ---
    public static final int THRESHOLD_HIGH = 90;
    public static final int THRESHOLD_MEDIUM = 70;

    // --- Alert types ---
    public static final String ALERT_SANCTION_HIT = "SANCTION_HIT";
    public static final String ALERT_PEP_HIT = "PEP_HIT";
    public static final String ALERT_ADVERSE_MEDIA = "ADVERSE_MEDIA";
    public static final String ALERT_HIGH_RISK = "HIGH_RISK";

    private final ScreeningAuditRepository screeningAuditRepository;
    private final ScreeningMatchRepository screeningMatchRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final List<ScreeningProvider> providers;

    public ScreeningService(ScreeningAuditRepository screeningAuditRepository,
                            ScreeningMatchRepository screeningMatchRepository,
                            AlertRepository alertRepository,
                            ObjectMapper objectMapper,
                            List<ScreeningProvider> providers) {
        this.screeningAuditRepository = screeningAuditRepository;
        this.screeningMatchRepository = screeningMatchRepository;
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
        this.providers = providers;
        log.info("AML screening initialized with {} providers: {}",
                providers.size(), providers.stream().map(ScreeningProvider::providerName).toList());
    }

    @Transactional
    public ScreeningResponse screen(ScreeningRequest request) {
        String screeningId = "scr_" + compactUuid();
        String screeningType = resolveScreeningType(request.screening_type());
        Instant now = Instant.now();

        // 1. Persist audit record (request input captured as JSON)
        ScreeningAuditEntity audit = new ScreeningAuditEntity();
        audit.setId(screeningId);
        audit.setCustomerId(request.customer_id());
        audit.setScreeningType(screeningType);
        audit.setRequestInput(serializeRequest(request));
        audit.setMatchCount(0);
        audit.setStatus(STATUS_CLEAR);
        audit.setTraceId(request.trace_id());
        audit.setActor(request.actor());
        audit.setSourceSystem(request.source_system());
        audit.setCreatedAt(now);

        // 2. Parse name from request
        ScreeningInput input = buildScreeningInput(request);

        // 3. Execute screening against all registered providers
        List<ScreeningMatchEntity> matches = new ArrayList<>();
        StringBuilder providerOutputs = new StringBuilder("[");
        boolean first = true;

        for (ScreeningProvider provider : providers) {
            try {
                List<ProviderMatch> providerMatches = provider.search(input);
                for (ProviderMatch pm : providerMatches) {
                    ScreeningMatchEntity match = new ScreeningMatchEntity();
                    match.setId("sm_" + compactUuid());
                    match.setScreeningId(screeningId);
                    match.setProvider(pm.provider());
                    match.setMatchScore(pm.matchScore());
                    match.setMatchType(pm.matchType());
                    match.setCategory(pm.category());
                    match.setEntityId(pm.entityId());
                    match.setEntityName(pm.entityName());
                    match.setEntityType(pm.entityType());
                    match.setDetailsJson(pm.detailsJson());
                    match.setCreatedAt(now);
                    matches.add(match);
                }
                if (!first) providerOutputs.append(",");
                providerOutputs.append("{\"provider\":\"").append(provider.providerName())
                        .append("\",\"matches\":").append(providerMatches.size()).append("}");
                first = false;
            } catch (Exception e) {
                log.error("Provider {} failed for screening {}: {}", provider.providerName(), screeningId, e.getMessage(), e);
                if (!first) providerOutputs.append(",");
                providerOutputs.append("{\"provider\":\"").append(provider.providerName())
                        .append("\",\"error\":\"").append(e.getMessage()).append("\"}");
                first = false;
            }
        }
        providerOutputs.append("]");
        audit.setProviderOutput(providerOutputs.toString());

        // 4. Score risk based on matches
        int riskScore = calculateRiskScore(matches);
        String riskLevel = classifyRiskLevel(riskScore);
        String status = matches.isEmpty() ? STATUS_CLEAR : STATUS_ALERT;

        audit.setMatchCount(matches.size());
        audit.setRiskScore(riskScore);
        audit.setRiskLevel(riskLevel);
        audit.setStatus(status);
        screeningAuditRepository.save(audit);

        // 5. Persist matches
        for (ScreeningMatchEntity match : matches) {
            screeningMatchRepository.save(match);
        }

        // 6. Auto-generate alerts for high/medium risk
        if (STATUS_ALERT.equals(status)) {
            generateAlerts(screeningId, request.customer_id(), riskLevel, matches, now);
        }

        // 7. Build response
        List<MatchResponse> matchResponses = matches.stream()
                .map(this::toMatchResponse)
                .toList();

        return new ScreeningResponse(
                screeningId,
                request.customer_id(),
                screeningType,
                status,
                riskLevel,
                riskScore,
                matches.size(),
                matchResponses,
                now);
    }

    @Transactional(readOnly = true)
    public ScreeningResponse getScreening(String screeningId) {
        ScreeningAuditEntity audit = screeningAuditRepository.findById(screeningId)
                .orElseThrow(() -> new ApiException("SCREENING_NOT_FOUND",
                        "screening not found: " + screeningId));

        List<ScreeningMatchEntity> matches =
                screeningMatchRepository.findByScreeningIdOrderByMatchScoreDesc(screeningId);

        List<MatchResponse> matchResponses = matches.stream()
                .map(this::toMatchResponse)
                .toList();

        return new ScreeningResponse(
                audit.getId(),
                audit.getCustomerId(),
                audit.getScreeningType(),
                audit.getStatus(),
                audit.getRiskLevel(),
                audit.getRiskScore() != null ? audit.getRiskScore() : 0,
                audit.getMatchCount(),
                matchResponses,
                audit.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ScreeningResponse> getScreeningsByCustomer(String customerId) {
        return screeningAuditRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(audit -> new ScreeningResponse(
                        audit.getId(),
                        audit.getCustomerId(),
                        audit.getScreeningType(),
                        audit.getStatus(),
                        audit.getRiskLevel(),
                        audit.getRiskScore() != null ? audit.getRiskScore() : 0,
                        audit.getMatchCount(),
                        List.of(),
                        audit.getCreatedAt()))
                .toList();
    }

    // --- Risk scoring (AML spec §4.4) ---

    int calculateRiskScore(List<ScreeningMatchEntity> matches) {
        if (matches.isEmpty()) {
            return 0;
        }
        int maxScore = 0;
        for (ScreeningMatchEntity match : matches) {
            int weighted = match.getMatchScore();
            if ("SANCTION".equals(match.getCategory())) {
                weighted = Math.max(weighted, 95);
            } else if ("PEP".equals(match.getCategory())) {
                weighted = Math.max(weighted, (int) (match.getMatchScore() * 1.1));
            }
            maxScore = Math.max(maxScore, weighted);
        }
        return Math.min(maxScore, 100);
    }

    String classifyRiskLevel(int riskScore) {
        if (riskScore >= THRESHOLD_HIGH) {
            return RISK_HIGH;
        } else if (riskScore >= THRESHOLD_MEDIUM) {
            return RISK_MEDIUM;
        }
        return RISK_LOW;
    }

    // --- Alert generation (AML spec §4.5) ---

    private void generateAlerts(String screeningId, String customerId, String riskLevel,
                                List<ScreeningMatchEntity> matches, Instant now) {
        String alertType = ALERT_HIGH_RISK;
        for (ScreeningMatchEntity match : matches) {
            if ("SANCTION".equals(match.getCategory())) {
                alertType = ALERT_SANCTION_HIT;
                break;
            } else if ("PEP".equals(match.getCategory())) {
                alertType = ALERT_PEP_HIT;
            } else if ("ADVERSE_MEDIA".equals(match.getCategory()) && !ALERT_PEP_HIT.equals(alertType)) {
                alertType = ALERT_ADVERSE_MEDIA;
            }
        }

        AlertEntity alert = new AlertEntity();
        alert.setId("alt_" + compactUuid());
        alert.setScreeningId(screeningId);
        alert.setCustomerId(customerId);
        alert.setAlertType(alertType);
        alert.setRiskLevel(riskLevel);
        alert.setStatus("OPEN");
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        alertRepository.save(alert);
    }

    // --- Input parsing ---

    private ScreeningInput buildScreeningInput(ScreeningRequest request) {
        String name = request.name();
        String firstName = null;
        String surname = null;

        if (name != null && name.contains(" ")) {
            String[] parts = name.trim().split("\\s+", 2);
            firstName = parts[0];
            surname = parts.length > 1 ? parts[1] : null;
        }

        return new ScreeningInput(
                firstName,
                surname,
                name,
                request.date_of_birth(),
                request.nationality(),
                request.id_number(),
                request.address());
    }

    // --- Helpers ---

    private String resolveScreeningType(String input) {
        if (input != null && VALID_SCREENING_TYPES.contains(input.toUpperCase())) {
            return input.toUpperCase();
        }
        return TYPE_INITIAL;
    }

    private MatchResponse toMatchResponse(ScreeningMatchEntity match) {
        return new MatchResponse(
                match.getId(),
                match.getProvider(),
                match.getMatchScore(),
                match.getMatchType(),
                match.getCategory(),
                match.getEntityId(),
                match.getEntityName(),
                match.getEntityType(),
                match.getCreatedAt());
    }

    private String serializeRequest(ScreeningRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
