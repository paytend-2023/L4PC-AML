package com.paytend.l4pc.aml.service.provider;

import com.paytend.l4pc.aml.domain.*;
import com.paytend.l4pc.aml.service.matching.NameMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Searches the internal watchlist (manually managed entries, local sanctions lists).
 * Same matching engine as DowJonesProvider but scoped to INTERNAL provider records.
 */
@Component
public class InternalListProvider implements ScreeningProvider {

    private static final Logger log = LoggerFactory.getLogger(InternalListProvider.class);
    private static final String PROVIDER = "INTERNAL";
    private static final int MIN_SCORE_THRESHOLD = 65;

    private final WatchlistNameRepository nameRepository;
    private final WatchlistEntityRepository entityRepository;
    private final WatchlistIdentityRepository identityRepository;
    private final NameMatcher nameMatcher;

    public InternalListProvider(WatchlistNameRepository nameRepository,
                                WatchlistEntityRepository entityRepository,
                                WatchlistIdentityRepository identityRepository,
                                NameMatcher nameMatcher) {
        this.nameRepository = nameRepository;
        this.entityRepository = entityRepository;
        this.identityRepository = identityRepository;
        this.nameMatcher = nameMatcher;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public List<ProviderMatch> search(ScreeningInput input) {
        // Search by ID number first (exact match, high confidence)
        List<ProviderMatch> results = new ArrayList<>();

        if (input.idNumber() != null && !input.idNumber().isBlank()) {
            List<WatchlistIdentityDO> idMatches = identityRepository.findByIdNumber(input.idNumber());
            for (WatchlistIdentityDO identity : idMatches) {
                Optional<WatchlistEntityDO> entityOpt = entityRepository.findById(identity.getEntityId());
                if (entityOpt.isEmpty() || !PROVIDER.equals(entityOpt.get().getProvider())) continue;
                if (!"ACTIVE".equals(entityOpt.get().getStatus())) continue;

                WatchlistEntityDO entity = entityOpt.get();
                List<WatchlistNameDO> names = nameRepository.findByEntityId(entity.getId());
                String displayName = names.isEmpty() ? entity.getExternalId()
                        : names.get(0).getFullName() != null ? names.get(0).getFullName()
                        : buildName(names.get(0));

                results.add(new ProviderMatch(
                        PROVIDER, 98, "EXACT", entity.getCategory(),
                        entity.getExternalId(), displayName, entity.getEntityType(), null));
            }
        }

        // Name-based search
        String queryName = buildQueryName(input);
        if (queryName.isBlank()) return results;

        String soundex = nameMatcher.soundex(nameMatcher.normalize(queryName));
        List<WatchlistNameDO> candidates = nameRepository.findBySoundexCode(soundex);

        // Also try substring
        String normalized = nameMatcher.normalize(queryName);
        if (normalized.length() >= 3) {
            candidates.addAll(nameRepository.findByNormalizedNameContaining(normalized));
        }

        // Filter to INTERNAL provider only and score
        Set<String> processedEntities = results.stream()
                .map(ProviderMatch::entityId)
                .collect(Collectors.toSet());

        Map<String, NameMatcher.MatchResult> bestByEntity = new HashMap<>();
        Map<String, WatchlistNameDO> bestNameByEntity = new HashMap<>();

        for (WatchlistNameDO candidate : candidates) {
            if (processedEntities.contains(candidate.getEntityId())) continue;

            String candidateName = candidate.getNormalizedName() != null
                    ? candidate.getNormalizedName() : buildName(candidate);
            NameMatcher.MatchResult result = nameMatcher.score(queryName, candidateName);

            if (result.score() >= MIN_SCORE_THRESHOLD) {
                NameMatcher.MatchResult existing = bestByEntity.get(candidate.getEntityId());
                if (existing == null || result.score() > existing.score()) {
                    bestByEntity.put(candidate.getEntityId(), result);
                    bestNameByEntity.put(candidate.getEntityId(), candidate);
                }
            }
        }

        for (var entry : bestByEntity.entrySet()) {
            Optional<WatchlistEntityDO> entityOpt = entityRepository.findById(entry.getKey());
            if (entityOpt.isEmpty() || !PROVIDER.equals(entityOpt.get().getProvider())) continue;
            if (!"ACTIVE".equals(entityOpt.get().getStatus())) continue;

            WatchlistEntityDO entity = entityOpt.get();
            WatchlistNameDO name = bestNameByEntity.get(entry.getKey());
            String displayName = name.getFullName() != null ? name.getFullName() : buildName(name);

            results.add(new ProviderMatch(
                    PROVIDER,
                    Math.min(entry.getValue().score(), 100),
                    entry.getValue().matchType(),
                    entity.getCategory(),
                    entity.getExternalId(),
                    displayName,
                    entity.getEntityType(),
                    null));
        }

        results.sort(Comparator.comparingInt(ProviderMatch::matchScore).reversed());
        return results.stream().limit(20).toList();
    }

    private String buildQueryName(ScreeningInput input) {
        if (input.fullName() != null && !input.fullName().isBlank()) return input.fullName();
        StringBuilder sb = new StringBuilder();
        if (input.firstName() != null) sb.append(input.firstName());
        if (input.surname() != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(input.surname());
        }
        return sb.toString();
    }

    private String buildName(WatchlistNameDO name) {
        if (name.getEntityName() != null && !name.getEntityName().isBlank()) return name.getEntityName();
        StringBuilder sb = new StringBuilder();
        if (name.getFirstName() != null) sb.append(name.getFirstName());
        if (name.getSurname() != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(name.getSurname());
        }
        return sb.toString();
    }
}
