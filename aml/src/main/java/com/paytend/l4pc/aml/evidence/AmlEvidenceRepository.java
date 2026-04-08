package com.paytend.l4pc.aml.evidence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AmlEvidenceRepository extends JpaRepository<AmlEvidenceRecord, String> {

    List<AmlEvidenceRecord> findByEvaluationId(String evaluationId);

    List<AmlEvidenceRecord> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<AmlEvidenceRecord> findByTraceId(String traceId);
}
