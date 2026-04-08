package com.paytend.l4pc.aml.restriction;

/**
 * Country restriction types.
 *
 * Migrated from:
 * - RiskCountryService: UNACCEPTABLE (transUnacceptCountry), HIGH_RISK (transHighRiskCountry)
 * - OrgRegistrationRestrictionService: RESTRICTED_REGISTRATION (by nationality/residence/registration country)
 */
public enum RestrictionType {
    UNACCEPTABLE,
    HIGH_RISK,
    RESTRICTED_REGISTRATION,
    RESTRICTED_TRADING
}
