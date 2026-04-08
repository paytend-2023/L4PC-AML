package com.paytend.l4pc.aml.service.provider;

/**
 * Normalized match result from a screening provider.
 */
public record ProviderMatch(
        String provider,
        int matchScore,
        String matchType,
        String category,
        String entityId,
        String entityName,
        String entityType,
        String detailsJson
) {
}
