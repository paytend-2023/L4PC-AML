package com.paytend.l4pc.aml.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Unified AML evaluation request from L3 domain layers.
 *
 * This is the primary contract between L3 (Bank/XPay) and PC-AML.
 * L3 sends this request; PC-AML returns {@link AmlEvaluationResponse}.
 *
 * Maps to:
 * - BankKycGateway.evaluateAml(AmlEvaluationRequest)
 * - CardRiskGateway.checkAmlDowJones(cardHolderId, traceId)
 */
public record AmlEvaluationRequest(
        @NotBlank String customerId,
        String customerName,
        String customerDateOfBirth,
        String customerNationality,
        String customerIdNumber,
        String counterpartyId,
        String counterpartyName,
        String counterpartyAccountNumber,
        String counterpartyCountry,
        String transactionType,
        long amountMinor,
        String currency,
        @NotBlank String traceId,
        String actor,
        String sourceSystem
) {}
