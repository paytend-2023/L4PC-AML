package com.paytend.l4pc.aml.service;

import com.paytend.l4pc.aml.blacklist.BlacklistEntry;
import com.paytend.l4pc.aml.blacklist.BlacklistRepository;
import com.paytend.l4pc.aml.blacklist.BlacklistType;
import com.paytend.l4pc.aml.model.AmlEvaluationRequest;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse.HitType;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse.RestrictionIntent;
import com.paytend.l4pc.aml.restriction.RestrictionType;
import com.paytend.l4pc.aml.restriction.RiskCountryEntry;
import com.paytend.l4pc.aml.restriction.RiskCountryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AmlEvaluationServiceTest {

    @Autowired AmlEvaluationService evaluationService;
    @Autowired BlacklistRepository blacklistRepository;
    @Autowired RiskCountryRepository riskCountryRepository;

    @Test
    void noHit_cleanCustomer() {
        AmlEvaluationRequest request = new AmlEvaluationRequest(
                "cust_clean_001", "John Smith", "1990-01-01", "GB", null,
                null, "Jane Doe", "GB1234567890", "GB",
                "SEPA_OUT", 100000, "EUR",
                "trace_" + UUID.randomUUID(), "test", "unit-test"
        );

        AmlEvaluationResponse response = evaluationService.evaluate(request);

        assertNotNull(response.evaluationId());
        assertFalse(response.hit());
        assertTrue(response.hits().isEmpty());
        assertEquals(RestrictionIntent.NONE, response.restrictionIntent());
        assertFalse(response.manualReviewRequired());
        assertEquals("NONE", response.overallRiskLevel());
    }

    @Test
    void blacklistHit_userBlocked() {
        BlacklistEntry entry = new BlacklistEntry();
        entry.setId("bl_test_" + UUID.randomUUID().toString().substring(0, 8));
        entry.setIdentifierType(BlacklistType.USER);
        entry.setIdentifierValue("cust_blacklisted_001");
        entry.setEntityName("Blocked User");
        entry.setReason("Fraud confirmed");
        entry.setStatus("ACTIVE");
        blacklistRepository.save(entry);

        AmlEvaluationRequest request = new AmlEvaluationRequest(
                "cust_blacklisted_001", "Blocked User", null, null, null,
                null, null, null, null,
                "SEPA_OUT", 50000, "EUR",
                "trace_" + UUID.randomUUID(), "test", "unit-test"
        );

        AmlEvaluationResponse response = evaluationService.evaluate(request);

        assertTrue(response.hit());
        assertFalse(response.hits().isEmpty());
        assertEquals(HitType.BLACKLIST_USER, response.hits().get(0).hitType());
        assertEquals(RestrictionIntent.BLOCK, response.restrictionIntent());
        assertEquals("HIGH", response.overallRiskLevel());
    }

    @Test
    void counterpartyBlacklist_externalAccount() {
        BlacklistEntry entry = new BlacklistEntry();
        entry.setId("bl_ext_" + UUID.randomUUID().toString().substring(0, 8));
        entry.setIdentifierType(BlacklistType.EXTERNAL_ACCOUNT);
        entry.setIdentifierValue("DE89370400440532013000");
        entry.setEntityName("Suspicious IBAN");
        entry.setReason("Reported for money laundering");
        entry.setStatus("ACTIVE");
        blacklistRepository.save(entry);

        AmlEvaluationRequest request = new AmlEvaluationRequest(
                "cust_sender_001", "Good Customer", null, null, null,
                null, "Suspicious Entity", "DE89370400440532013000", "DE",
                "SEPA_OUT", 200000, "EUR",
                "trace_" + UUID.randomUUID(), "test", "unit-test"
        );

        AmlEvaluationResponse response = evaluationService.evaluate(request);

        assertTrue(response.hit());
        assertTrue(response.hits().stream().anyMatch(h -> h.hitType() == HitType.BLACKLIST_COUNTERPARTY));
        assertEquals(RestrictionIntent.BLOCK, response.restrictionIntent());
    }

    @Test
    void highRiskCountry_manualReview() {
        RiskCountryEntry rc = new RiskCountryEntry();
        rc.setId("rc_test_" + UUID.randomUUID().toString().substring(0, 8));
        rc.setCountryCode("IR");
        rc.setCountryName("Iran");
        rc.setRestrictionType(RestrictionType.HIGH_RISK);
        rc.setStatus("ACTIVE");
        rc.setReason("FATF high-risk jurisdiction");
        riskCountryRepository.save(rc);

        AmlEvaluationRequest request = new AmlEvaluationRequest(
                "cust_ir_001", "Customer X", null, null, null,
                null, "Receiver Y", null, "IR",
                "SWIFT_OUT", 500000, "EUR",
                "trace_" + UUID.randomUUID(), "test", "unit-test"
        );

        AmlEvaluationResponse response = evaluationService.evaluate(request);

        assertTrue(response.hit());
        assertTrue(response.hits().stream().anyMatch(h -> h.hitType() == HitType.HIGH_RISK_COUNTRY));
        assertTrue(response.manualReviewRequired());
    }

    @Test
    void unacceptableCountry_blocked() {
        RiskCountryEntry rc = new RiskCountryEntry();
        rc.setId("rc_unac_" + UUID.randomUUID().toString().substring(0, 8));
        rc.setCountryCode("KP");
        rc.setCountryName("North Korea");
        rc.setRestrictionType(RestrictionType.UNACCEPTABLE);
        rc.setStatus("ACTIVE");
        rc.setReason("Comprehensive sanctions");
        riskCountryRepository.save(rc);

        AmlEvaluationRequest request = new AmlEvaluationRequest(
                "cust_kp_001", "Customer Z", null, null, null,
                null, "Entity in DPRK", null, "KP",
                "SWIFT_OUT", 100000, "EUR",
                "trace_" + UUID.randomUUID(), "test", "unit-test"
        );

        AmlEvaluationResponse response = evaluationService.evaluate(request);

        assertTrue(response.hit());
        assertTrue(response.hits().stream().anyMatch(h -> h.hitType() == HitType.UNACCEPTABLE_COUNTRY));
        assertEquals(RestrictionIntent.BLOCK, response.restrictionIntent());
        assertEquals("HIGH", response.overallRiskLevel());
    }
}
