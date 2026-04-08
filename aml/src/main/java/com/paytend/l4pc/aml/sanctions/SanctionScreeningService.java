package com.paytend.l4pc.aml.sanctions;

import com.paytend.l4pc.aml.model.AmlEvaluationResponse.AmlHit;
import com.paytend.l4pc.aml.model.AmlEvaluationResponse.HitType;
import com.paytend.l4pc.aml.service.provider.ProviderMatch;
import com.paytend.l4pc.aml.service.provider.ScreeningInput;
import com.paytend.l4pc.aml.service.provider.ScreeningProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanctions screening service — orchestrates all ScreeningProviders (DowJones, EU, UN, Internal).
 *
 * Replaces the sanction-related parts of:
 * - TradeCheckService.checkTrade step 22 (oDDService.sendAmlTradeCheck)
 * - DowjonesInfService.sendDowjones
 * - SalvAmlService (AML screening portion)
 * - MasterCardService.dowjonesCheckCradHolder (CardRiskGateway.checkAmlDowJones)
 *
 * This service delegates to ScreeningProviders, converts results to AmlHit format,
 * and classifies hit types (SANCTION vs PEP vs WATCHLIST).
 */
@Service
public class SanctionScreeningService {

    private static final Logger log = LoggerFactory.getLogger(SanctionScreeningService.class);
    private static final int PEP_SCORE_THRESHOLD = 70;

    private final List<ScreeningProvider> providers;

    public SanctionScreeningService(List<ScreeningProvider> providers) {
        this.providers = providers;
    }

    /**
     * Screen a subject against all sanction/watchlist providers.
     */
    public List<AmlHit> screen(String fullName, String dateOfBirth, String nationality,
                               String idNumber, String address) {
        List<AmlHit> hits = new ArrayList<>();

        String firstName = null;
        String surname = null;
        if (fullName != null && fullName.contains(" ")) {
            int idx = fullName.lastIndexOf(' ');
            firstName = fullName.substring(0, idx).trim();
            surname = fullName.substring(idx + 1).trim();
        }

        ScreeningInput input = new ScreeningInput(firstName, surname, fullName, dateOfBirth, nationality, idNumber, address);

        for (ScreeningProvider provider : providers) {
            try {
                List<ProviderMatch> matches = provider.search(input);
                for (ProviderMatch match : matches) {
                    HitType hitType = classifyHitType(match);
                    String reasonCode = hitType == HitType.PEP ? "PEP_HIT" :
                            hitType == HitType.SANCTION ? "SANCTION_HIT" : "WATCHLIST_HIT";

                    hits.add(new AmlHit(
                            hitType,
                            reasonCode,
                            match.entityId(),
                            match.entityName(),
                            match.provider(),
                            match.matchScore(),
                            match.category(),
                            match.entityId(),
                            match.detailsJson()
                    ));
                }
            } catch (Exception e) {
                log.error("Sanction screening provider {} failed: {}", provider.providerName(), e.getMessage(), e);
            }
        }

        return hits;
    }

    /**
     * Screen a counterparty (beneficiary / payer).
     */
    public List<AmlHit> screenCounterparty(String counterpartyName, String counterpartyCountry) {
        if (counterpartyName == null || counterpartyName.isBlank()) return List.of();
        return screen(counterpartyName, null, counterpartyCountry, null, null);
    }

    private HitType classifyHitType(ProviderMatch match) {
        String category = match.category() != null ? match.category().toUpperCase() : "";
        if (category.contains("PEP") || category.contains("POLITICALLY EXPOSED")) return HitType.PEP;
        if (category.contains("SANCTION") || category.contains("SANCTIONED")) return HitType.SANCTION;
        if (category.contains("ADVERSE") || category.contains("MEDIA")) return HitType.ADVERSE_MEDIA;
        return HitType.WATCHLIST;
    }
}
