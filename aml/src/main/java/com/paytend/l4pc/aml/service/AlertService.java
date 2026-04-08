package com.paytend.l4pc.aml.service;

import com.paytend.l4pc.aml.api.*;
import com.paytend.l4pc.aml.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AlertService {

    // --- Alert statuses (AML spec §4.5: 新建→审核中→升级→通过/拒绝) ---
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";
    public static final String STATUS_ESCALATED = "ESCALATED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private static final Set<String> VALID_STATUSES = Set.of(
            STATUS_OPEN, STATUS_UNDER_REVIEW, STATUS_ESCALATED, STATUS_APPROVED, STATUS_REJECTED);

    // --- Decision types ---
    public static final String DECISION_APPROVE = "APPROVE";
    public static final String DECISION_REJECT = "REJECT";
    public static final String DECISION_ESCALATE = "ESCALATE";

    private static final Set<String> VALID_DECISIONS = Set.of(
            DECISION_APPROVE, DECISION_REJECT, DECISION_ESCALATE);

    private final AlertRepository alertRepository;
    private final CaseDecisionRepository caseDecisionRepository;

    public AlertService(AlertRepository alertRepository,
                        CaseDecisionRepository caseDecisionRepository) {
        this.alertRepository = alertRepository;
        this.caseDecisionRepository = caseDecisionRepository;
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(String alertId) {
        AlertEntity alert = findAlertOrThrow(alertId);
        List<CaseDecisionResponse> decisions = loadDecisions(alertId);
        return toAlertResponse(alert, decisions);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByStatus(String status) {
        return alertRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(alert -> toAlertResponse(alert, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByCustomer(String customerId) {
        return alertRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(alert -> toAlertResponse(alert, List.of()))
                .toList();
    }

    @Transactional
    public AlertResponse updateAlert(String alertId, AlertUpdateRequest request) {
        AlertEntity alert = findAlertOrThrow(alertId);

        if (request.status() != null) {
            String newStatus = request.status().toUpperCase();
            if (!VALID_STATUSES.contains(newStatus)) {
                throw new ApiException("INVALID_STATUS",
                        "invalid alert status: " + newStatus + ", valid: " + VALID_STATUSES);
            }
            validateStatusTransition(alert.getStatus(), newStatus);
            alert.setStatus(newStatus);
        }
        if (request.assigned_to() != null) {
            alert.setAssignedTo(request.assigned_to());
        }
        alert.setUpdatedAt(Instant.now());
        alertRepository.save(alert);

        List<CaseDecisionResponse> decisions = loadDecisions(alertId);
        return toAlertResponse(alert, decisions);
    }

    @Transactional
    public CaseDecisionResponse addDecision(String alertId, CaseDecisionRequest request) {
        AlertEntity alert = findAlertOrThrow(alertId);

        String decision = request.decision().toUpperCase();
        if (!VALID_DECISIONS.contains(decision)) {
            throw new ApiException("INVALID_DECISION",
                    "invalid decision: " + decision + ", valid: " + VALID_DECISIONS);
        }

        // AML spec §11: high risk requires dual approval (maker-checker)
        if ("HIGH".equals(alert.getRiskLevel())
                && !DECISION_ESCALATE.equals(decision)
                && (request.approved_by() == null || request.approved_by().isBlank())) {
            throw new ApiException("DUAL_APPROVAL_REQUIRED",
                    "high risk alerts require dual approval (approved_by is mandatory)");
        }

        Instant now = Instant.now();
        CaseDecisionEntity entity = new CaseDecisionEntity();
        entity.setId("cd_" + UUID.randomUUID().toString().replace("-", ""));
        entity.setAlertId(alertId);
        entity.setDecision(decision);
        entity.setDecisionReason(request.decision_reason());
        entity.setDecidedBy(request.decided_by());
        entity.setApprovedBy(request.approved_by());
        entity.setAttachments(request.attachments());
        entity.setCreatedAt(now);
        caseDecisionRepository.save(entity);

        // Auto-transition alert status based on decision
        String newStatus = switch (decision) {
            case DECISION_APPROVE -> STATUS_APPROVED;
            case DECISION_REJECT -> STATUS_REJECTED;
            case DECISION_ESCALATE -> STATUS_ESCALATED;
            default -> alert.getStatus();
        };
        alert.setStatus(newStatus);
        alert.setUpdatedAt(now);
        alertRepository.save(alert);

        return new CaseDecisionResponse(
                entity.getId(),
                entity.getDecision(),
                entity.getDecisionReason(),
                entity.getDecidedBy(),
                entity.getApprovedBy(),
                entity.getCreatedAt());
    }

    // --- Helpers ---

    private AlertEntity findAlertOrThrow(String alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException("ALERT_NOT_FOUND", "alert not found: " + alertId));
    }

    private List<CaseDecisionResponse> loadDecisions(String alertId) {
        return caseDecisionRepository.findByAlertIdOrderByCreatedAtDesc(alertId).stream()
                .map(d -> new CaseDecisionResponse(
                        d.getId(), d.getDecision(), d.getDecisionReason(),
                        d.getDecidedBy(), d.getApprovedBy(), d.getCreatedAt()))
                .toList();
    }

    private AlertResponse toAlertResponse(AlertEntity alert, List<CaseDecisionResponse> decisions) {
        return new AlertResponse(
                alert.getId(),
                alert.getScreeningId(),
                alert.getCustomerId(),
                alert.getAlertType(),
                alert.getRiskLevel(),
                alert.getStatus(),
                alert.getAssignedTo(),
                decisions,
                alert.getCreatedAt(),
                alert.getUpdatedAt());
    }

    private void validateStatusTransition(String current, String target) {
        // Closed states cannot be reopened
        if ((STATUS_APPROVED.equals(current) || STATUS_REJECTED.equals(current))
                && !current.equals(target)) {
            throw new ApiException("INVALID_STATUS_TRANSITION",
                    "cannot transition from " + current + " to " + target + ": alert is closed");
        }
    }
}
