package com.paytend.l4pc.aml.model;

/**
 * Enumeration of all AML check types performed by pc-aml.
 * Maps to legacy TradeCheckService check steps.
 */
public enum AmlCheckType {
    /** Sanctions screening (DowJones, EU, UN, internal watchlist) */
    SANCTION_SCREENING,
    /** User blacklist check — legacy: checkUserBlack */
    USER_BLACKLIST,
    /** Counterparty/beneficiary blacklist — legacy: checkTransferBlackList */
    COUNTERPARTY_BLACKLIST,
    /** High-risk country — legacy: checkRiskCountryAmount */
    HIGH_RISK_COUNTRY,
    /** Unacceptable country — legacy: checkRiskCountryAmount (transUnacceptCountry) */
    UNACCEPTABLE_COUNTRY,
    /** Organization registration restriction — legacy: OrgRegistrationRestrictionService */
    ORG_REGISTRATION_RESTRICTION,
    /** PEP/RCA screening — legacy: oDDService.sendAmlTradeCheck (step 22) */
    PEP_SCREENING,
    /** DowJones whitelist exemption check */
    WHITELIST_EXEMPTION
}
