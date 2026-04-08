package com.paytend.l4pc.aml.blacklist;

/**
 * Blacklist identifier types.
 *
 * Migrated from:
 * - BlacklistService: USER, MOBILE, IDNO, PASSPORT, IP, ACCOUNT, BANK
 * - TransactionBlacklistService: EXTERNAL_ACCOUNT (type=2)
 */
public enum BlacklistType {
    USER,
    MOBILE,
    IDNO,
    PASSPORT,
    IP_ADDRESS,
    ACCOUNT,
    BANK,
    EXTERNAL_ACCOUNT
}
