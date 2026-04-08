package com.paytend.l4pc.aml.restriction;

import com.paytend.l4pc.aml.model.AmlEvaluationResponse.AmlHit;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse.HitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Country restriction and org registration restriction check.
 *
 * Replaces:
 * - RiskCountryService.isCanTrade / checkCountryCanTrade
 * - RiskCountryService.isCanRegister
 * - TradeCheckService.checkRiskCountryAmount
 * - OrgRegistrationRestrictionService.isOrgAllowedToRegister
 *
 * PC-AML only detects and reports — outputs restriction_intent suggestion.
 */
@Service
public class RestrictionCheckService {

    private static final Logger log = LoggerFactory.getLogger(RestrictionCheckService.class);

    private final RiskCountryRepository riskCountryRepository;
    private final OrgRestrictionRepository orgRestrictionRepository;

    public RestrictionCheckService(RiskCountryRepository riskCountryRepository,
                                   OrgRestrictionRepository orgRestrictionRepository) {
        this.riskCountryRepository = riskCountryRepository;
        this.orgRestrictionRepository = orgRestrictionRepository;
    }

    /**
     * Check counterparty country against risk country lists.
     * Maps to TradeCheckService.checkRiskCountryAmount.
     */
    public List<AmlHit> checkCountryRisk(String counterpartyCountry) {
        List<AmlHit> hits = new ArrayList<>();
        if (counterpartyCountry == null || counterpartyCountry.isBlank()) return hits;

        String code = counterpartyCountry.trim().toUpperCase();

        List<RiskCountryEntry> entries = riskCountryRepository.findActiveByCountryCode(code);
        for (RiskCountryEntry entry : entries) {
            HitType hitType = entry.getRestrictionType() == RestrictionType.UNACCEPTABLE
                    ? HitType.UNACCEPTABLE_COUNTRY
                    : HitType.HIGH_RISK_COUNTRY;
            String reasonCode = entry.getRestrictionType() == RestrictionType.UNACCEPTABLE
                    ? "UNACCEPTABLE_COUNTRY"
                    : "HIGH_RISK_COUNTRY";

            hits.add(new AmlHit(
                    hitType,
                    reasonCode,
                    entry.getId(),
                    entry.getCountryName() != null ? entry.getCountryName() : code,
                    "RISK_COUNTRY_LIST",
                    100,
                    entry.getRestrictionType().name(),
                    entry.getId(),
                    entry.getReason()
            ));
            log.info("Country risk hit: country={}, type={}", code, entry.getRestrictionType());
        }

        return hits;
    }

    /**
     * Check org registration restriction.
     * Maps to OrgRegistrationRestrictionService.isOrgAllowedToRegister.
     *
     * @param nationality         ISO 3166-1 alpha-2
     * @param residenceCountry    ISO 3166-1 alpha-2
     * @param registrationCountry ISO 3166-1 alpha-2
     */
    public List<AmlHit> checkOrgRegistrationRestriction(String nationality, String residenceCountry, String registrationCountry) {
        List<AmlHit> hits = new ArrayList<>();

        checkOrgField(hits, nationality, "NATIONALITY");
        checkOrgField(hits, residenceCountry, "RESIDENCE_COUNTRY");
        checkOrgField(hits, registrationCountry, "REGISTRATION_COUNTRY");

        return hits;
    }

    private void checkOrgField(List<AmlHit> hits, String countryCode, String field) {
        if (countryCode == null || countryCode.isBlank()) return;
        String code = countryCode.trim().toUpperCase();

        List<OrgRestrictionEntry> entries = orgRestrictionRepository.findActiveByCountryCodeAndField(code, field);
        for (OrgRestrictionEntry entry : entries) {
            hits.add(new AmlHit(
                    HitType.ORG_REGISTRATION_RESTRICTED,
                    "ORG_REGISTRATION_RESTRICTED_" + field,
                    entry.getId(),
                    code,
                    "ORG_RESTRICTION_LIST",
                    100,
                    field,
                    entry.getId(),
                    entry.getReason()
            ));
            log.info("Org restriction hit: country={}, field={}", code, field);
        }
    }
}
