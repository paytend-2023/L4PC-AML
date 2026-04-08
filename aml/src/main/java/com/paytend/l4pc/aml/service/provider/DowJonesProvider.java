package com.paytend.l4pc.aml.service.provider;

import com.paytend.l4pc.aml.domain.*;
import com.paytend.l4pc.aml.service.matching.NameMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Searches the local watchlist database populated from Dow Jones data feeds.
 * Matching is performed against pre-indexed name records using:
 * - Soundex phonetic lookup (fast candidate retrieval)
 * - Name similarity scoring (Levenshtein + token matching)
 * - Nationality and DOB cross-validation (score boosting/penalty)
 */
@Component
public class DowJonesProvider implements ScreeningProvider {

    private static final Logger log = LoggerFactory.getLogger(DowJonesProvider.class);
    private static final String PROVIDER = "DOW_JONES";
    private static final int MAX_CANDIDATES = 200;
    private static final int MIN_SCORE_THRESHOLD = 60;

    private final WatchlistNameRepository nameRepository;
    private final WatchlistEntityRepository entityRepository;
    private final WatchlistIdentityRepository identityRepository;
    private final NameMatcher nameMatcher;

    public DowJonesProvider(WatchlistNameRepository nameRepository,
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
        // 1. Retrieve candidates via multiple strategies
        Set<WatchlistNameDO> candidates = new LinkedHashSet<>();

        // Strategy A: Soundex lookup (phonetic)
        if (input.fullName() != null && !input.fullName().isBlank()) {
            String soundex = nameMatcher.soundex(nameMatcher.normalize(input.fullName()));
            candidates.addAll(nameRepository.findBySoundexCode(soundex));
        }
        if (input.firstName() != null && !input.firstName().isBlank()) {
            String soundex = nameMatcher.soundex(nameMatcher.normalize(input.firstName()));
            candidates.addAll(nameRepository.findBySoundexCode(soundex));
        }
        if (input.surname() != null && !input.surname().isBlank()) {
            String soundex = nameMatcher.soundex(nameMatcher.normalize(input.surname()));
            candidates.addAll(nameRepository.findBySoundexCode(soundex));
        }

        // Strategy B: Substring lookup (catches non-phonetic matches)
        String searchTerm = input.surname() != null ? input.surname() : input.fullName();
        if (searchTerm != null && searchTerm.length() >= 3) {
            String normalized = nameMatcher.normalize(searchTerm);
            List<WatchlistNameDO> substringMatches = nameRepository.findByNormalizedNameContaining(normalized);
            if (substringMatches.size() <= MAX_CANDIDATES) {
                candidates.addAll(substringMatches);
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. Score each candidate
        String queryName = buildQueryName(input);
        List<ScoredCandidate> scored = new ArrayList<>();

        for (WatchlistNameDO candidate : candidates) {
            String candidateName = candidate.getNormalizedName() != null
                    ? candidate.getNormalizedName()
                    : buildCandidateName(candidate);

            NameMatcher.MatchResult result = nameMatcher.score(queryName, candidateName);

            // Also try alias matching: score individual parts
            if (input.firstName() != null && input.surname() != null && candidate.getFirstName() != null && candidate.getSurname() != null) {
                NameMatcher.MatchResult firstResult = nameMatcher.score(input.firstName(), candidate.getFirstName());
                NameMatcher.MatchResult surnameResult = nameMatcher.score(input.surname(), candidate.getSurname());
                int compositeScore = (firstResult.score() + surnameResult.score()) / 2;
                if (compositeScore > result.score()) {
                    result = new NameMatcher.MatchResult(compositeScore, result.matchType());
                }

                // Try reversed order (surname as first, first as surname)
                NameMatcher.MatchResult reversedFirst = nameMatcher.score(input.firstName(), candidate.getSurname());
                NameMatcher.MatchResult reversedSurname = nameMatcher.score(input.surname(), candidate.getFirstName());
                int reversedScore = (reversedFirst.score() + reversedSurname.score()) / 2;
                if (reversedScore > result.score()) {
                    result = new NameMatcher.MatchResult(reversedScore, "ALIAS");
                }
            }

            if (result.score() >= MIN_SCORE_THRESHOLD) {
                scored.add(new ScoredCandidate(candidate, result));
            }
        }

        // 3. Cross-validate with identity data (DOB, nationality, ID number) — boost or penalize
        Map<String, List<ScoredCandidate>> byEntity = scored.stream()
                .collect(Collectors.groupingBy(WatchlistNameDO -> WatchlistNameDO.name().getEntityId()));

        List<ProviderMatch> results = new ArrayList<>();
        for (var entry : byEntity.entrySet()) {
            String entityId = entry.getKey();
            ScoredCandidate best = entry.getValue().stream()
                    .max(Comparator.comparingInt(s -> s.result().score()))
                    .orElse(null);
            if (best == null) continue;

            int finalScore = best.result().score();
            String matchType = best.result().matchType();

            // Identity cross-validation
            finalScore = applyIdentityBoost(entityId, input, finalScore);

            if (finalScore < MIN_SCORE_THRESHOLD) continue;
            finalScore = Math.min(finalScore, 100);

            // Resolve entity metadata
            Optional<WatchlistEntityDO> entityOpt = entityRepository.findById(entityId);
            if (entityOpt.isEmpty() || !"ACTIVE".equals(entityOpt.get().getStatus())) continue;

            WatchlistEntityDO entity = entityOpt.get();
            String displayName = best.name().getFullName() != null
                    ? best.name().getFullName()
                    : buildCandidateName(best.name());

            results.add(new ProviderMatch(
                    PROVIDER,
                    finalScore,
                    matchType,
                    entity.getCategory(),
                    entity.getExternalId(),
                    displayName,
                    entity.getEntityType(),
                    null));
        }

        results.sort(Comparator.comparingInt(ProviderMatch::matchScore).reversed());
        return results.stream().limit(20).toList();
    }

    private int applyIdentityBoost(String entityId, ScreeningInput input, int baseScore) {
        List<WatchlistIdentityDO> identities = identityRepository.findByEntityId(entityId);
        if (identities.isEmpty()) return baseScore;

        int boost = 0;
        for (WatchlistIdentityDO identity : identities) {
            // DOB match: strong signal
            if (input.dateOfBirth() != null && identity.getDateOfBirth() != null
                    && input.dateOfBirth().equals(identity.getDateOfBirth())) {
                boost = Math.max(boost, 10);
            }
            // Nationality match
            if (input.nationality() != null && identity.getNationality() != null
                    && input.nationality().equalsIgnoreCase(identity.getNationality())) {
                boost = Math.max(boost, 5);
            }
            // ID number exact match: very strong signal
            if (input.idNumber() != null && identity.getIdNumber() != null
                    && input.idNumber().equalsIgnoreCase(identity.getIdNumber())) {
                boost = Math.max(boost, 15);
            }
        }
        return baseScore + boost;
    }

    private String buildQueryName(ScreeningInput input) {
        if (input.fullName() != null && !input.fullName().isBlank()) {
            return input.fullName();
        }
        StringBuilder sb = new StringBuilder();
        if (input.firstName() != null) sb.append(input.firstName());
        if (input.surname() != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(input.surname());
        }
        return sb.toString();
    }

    private String buildCandidateName(WatchlistNameDO name) {
        if (name.getEntityName() != null && !name.getEntityName().isBlank()) {
            return name.getEntityName();
        }
        StringBuilder sb = new StringBuilder();
        if (name.getFirstName() != null) sb.append(name.getFirstName());
        if (name.getSurname() != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(name.getSurname());
        }
        return sb.toString();
    }

    private record ScoredCandidate(WatchlistNameDO name, NameMatcher.MatchResult result) {
    }
}
