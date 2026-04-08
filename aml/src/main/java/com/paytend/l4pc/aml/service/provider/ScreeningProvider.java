package com.paytend.l4pc.aml.service.provider;

import java.util.List;

/**
 * Abstraction for AML data providers (Dow Jones, Refinitiv, EU/UN sanctions, internal lists).
 * Each provider is responsible for querying its own data source and returning normalized matches.
 */
public interface ScreeningProvider {

    String providerName();

    List<ProviderMatch> search(ScreeningInput input);
}
