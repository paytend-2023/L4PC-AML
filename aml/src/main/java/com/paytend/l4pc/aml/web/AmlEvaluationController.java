package com.paytend.l4pc.aml.web;

import com.paytend.l4pc.aml.evidence.AmlEvidenceRecord;
import com.paytend.l4pc.aml.evidence.AmlEvidenceRepository;
import com.paytend.l4pc.aml.model.AmlEvaluationRequest;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse;
import com.paytend.l4pc.aml.service.AmlEvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Primary API for L3 domain layers.
 *
 * Contract:
 * - POST /pc/aml/evaluate — run full AML evaluation (blacklist + sanctions + country)
 * - GET  /pc/aml/evidence/{evaluationId} — retrieve evidence for a past evaluation
 * - GET  /pc/aml/evidence?traceId=xxx — retrieve evidence by trace ID
 *
 * This is the endpoint that BankKycGateway.evaluateAml() and
 * CardRiskGateway.checkAmlDowJones() will call.
 */
@RestController
@RequestMapping("/pc/aml")
public class AmlEvaluationController {

    private final AmlEvaluationService evaluationService;
    private final AmlEvidenceRepository evidenceRepository;

    public AmlEvaluationController(AmlEvaluationService evaluationService,
                                   AmlEvidenceRepository evidenceRepository) {
        this.evaluationService = evaluationService;
        this.evidenceRepository = evidenceRepository;
    }

    @PostMapping("/evaluate")
    @ResponseStatus(HttpStatus.OK)
    public AmlEvaluationResponse evaluate(@Valid @RequestBody AmlEvaluationRequest request) {
        return evaluationService.evaluate(request);
    }

    @GetMapping("/evidence/{evaluationId}")
    public List<AmlEvidenceRecord> getEvidenceByEvaluation(@PathVariable String evaluationId) {
        return evidenceRepository.findByEvaluationId(evaluationId);
    }

    @GetMapping("/evidence")
    public List<AmlEvidenceRecord> getEvidence(
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "customerId", required = false) String customerId) {
        if (traceId != null) return evidenceRepository.findByTraceId(traceId);
        if (customerId != null) return evidenceRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return List.of();
    }
}
