package com.paytend.l4pc.aml.blacklist;

import com.paytend.l4pc.aml.model.AmlEvaluationResponse.AmlHit;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse.HitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Blacklist check service.
 *
 * Replaces:
 * - BlacklistService.checkAccountBlackList (user-level blacklist)
 * - TransactionBlacklistService.checkBlacklistVerification (counterparty blacklist)
 * - TradeCheckService.checkUserBlack (step 2)
 * - TradeCheckService.checkTransferBlackList (step 4)
 *
 * PC-AML only detects and reports — does NOT execute block/freeze.
 */
@Service
public class BlacklistCheckService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistCheckService.class);

    private final BlacklistRepository blacklistRepository;

    public BlacklistCheckService(BlacklistRepository blacklistRepository) {
        this.blacklistRepository = blacklistRepository;
    }

    /**
     * Check whether a customer is on any blacklist.
     * Searches by user ID, mobile, idno, passport.
     */
    public List<AmlHit> checkCustomer(String customerId, String mobile, String idno, String passport) {
        List<AmlHit> hits = new ArrayList<>();

        checkIdentifier(hits, BlacklistType.USER, customerId, "USER_BLACKLISTED");
        if (mobile != null) checkIdentifier(hits, BlacklistType.MOBILE, mobile, "MOBILE_BLACKLISTED");
        if (idno != null) checkIdentifier(hits, BlacklistType.IDNO, idno, "IDNO_BLACKLISTED");
        if (passport != null) checkIdentifier(hits, BlacklistType.PASSPORT, passport, "PASSPORT_BLACKLISTED");

        return hits;
    }

    /**
     * Check whether a counterparty account is blacklisted.
     * Maps to: TransactionBlacklistService.checkBlacklistVerification
     */
    public List<AmlHit> checkCounterparty(String accountNumber, String accountName) {
        List<AmlHit> hits = new ArrayList<>();

        if (accountNumber != null) {
            checkIdentifier(hits, BlacklistType.EXTERNAL_ACCOUNT, accountNumber, "COUNTERPARTY_ACCOUNT_BLACKLISTED");
            checkIdentifier(hits, BlacklistType.ACCOUNT, accountNumber, "COUNTERPARTY_ACCOUNT_BLACKLISTED");
        }

        return hits;
    }

    private void checkIdentifier(List<AmlHit> hits, BlacklistType type, String value, String reasonCode) {
        List<BlacklistEntry> entries = blacklistRepository.findActiveByTypeAndValue(type, value.trim());
        for (BlacklistEntry entry : entries) {
            HitType hitType = (type == BlacklistType.EXTERNAL_ACCOUNT || type == BlacklistType.ACCOUNT)
                    ? HitType.BLACKLIST_COUNTERPARTY
                    : HitType.BLACKLIST_USER;

            hits.add(new AmlHit(
                    hitType,
                    reasonCode,
                    entry.getId(),
                    entry.getEntityName(),
                    "INTERNAL_BLACKLIST",
                    100,
                    type.name(),
                    entry.getId(),
                    entry.getReason()
            ));
            log.info("Blacklist hit: type={}, value={}, entryId={}", type, value, entry.getId());
        }
    }
}
