package com.paytend.l4pc.aml.web;

import com.paytend.l4pc.aml.api.*;
import com.paytend.l4pc.aml.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pc/aml/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/{alertId}")
    public AlertResponse getAlert(@PathVariable String alertId) {
        return alertService.getAlert(alertId);
    }

    @GetMapping
    public List<AlertResponse> getAlerts(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "customer_id", required = false) String customerId) {
        if (customerId != null) {
            return alertService.getAlertsByCustomer(customerId);
        }
        if (status != null) {
            return alertService.getAlertsByStatus(status.toUpperCase());
        }
        return alertService.getAlertsByStatus("OPEN");
    }

    @PatchMapping("/{alertId}")
    public AlertResponse updateAlert(@PathVariable String alertId,
                                     @RequestBody AlertUpdateRequest request) {
        return alertService.updateAlert(alertId, request);
    }

    @PostMapping("/{alertId}/decisions")
    public CaseDecisionResponse addDecision(@PathVariable String alertId,
                                            @Valid @RequestBody CaseDecisionRequest request) {
        return alertService.addDecision(alertId, request);
    }
}
