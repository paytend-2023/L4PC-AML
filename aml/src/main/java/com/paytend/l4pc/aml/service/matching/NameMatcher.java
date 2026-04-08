package com.paytend.l4pc.aml.service.matching;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Name matching engine supporting multiple algorithms per AML spec §4.3:
 * - Exact match
 * - Fuzzy match (Levenshtein edit distance)
 * - Phonetic match (Soundex)
 * - Normalized comparison (case, whitespace, diacritics)
 */
@Component
public class NameMatcher {

    /**
     * Calculate overall match score (0-100) between a query name and a candidate name.
     */
    public MatchResult score(String queryName, String candidateName) {
        if (queryName == null || candidateName == null) {
            return new MatchResult(0, "NONE");
        }

        String normalizedQuery = normalize(queryName);
        String normalizedCandidate = normalize(candidateName);

        if (normalizedQuery.isEmpty() || normalizedCandidate.isEmpty()) {
            return new MatchResult(0, "NONE");
        }

        // 1. Exact match
        if (normalizedQuery.equals(normalizedCandidate)) {
            return new MatchResult(100, "EXACT");
        }

        // 2. Phonetic match (Soundex)
        String querySoundex = soundex(normalizedQuery);
        String candidateSoundex = soundex(normalizedCandidate);
        boolean phoneticMatch = querySoundex.equals(candidateSoundex);

        // 3. Fuzzy match (Levenshtein similarity)
        int fuzzyScore = levenshteinSimilarity(normalizedQuery, normalizedCandidate);

        // 4. Token-based match (handles reordered name parts)
        int tokenScore = tokenSimilarity(normalizedQuery, normalizedCandidate);

        // Combine scores: take best of fuzzy and token, boost if phonetic match
        int bestScore = Math.max(fuzzyScore, tokenScore);
        if (phoneticMatch && bestScore < 95) {
            bestScore = Math.max(bestScore, 85);
        }

        String matchType;
        if (bestScore >= 95) {
            matchType = "EXACT";
        } else if (phoneticMatch && bestScore >= 80) {
            matchType = "PHONETIC";
        } else if (bestScore >= 70) {
            matchType = "FUZZY";
        } else {
            matchType = "FUZZY";
        }

        return new MatchResult(bestScore, matchType);
    }

    // --- Levenshtein similarity ---

    int levenshteinSimilarity(String a, String b) {
        int distance = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 100;
        return (int) Math.round((1.0 - (double) distance / maxLen) * 100);
    }

    int levenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lenB];
    }

    // --- Token-based similarity (handles name reordering) ---

    int tokenSimilarity(String query, String candidate) {
        String[] queryTokens = query.split("\\s+");
        String[] candidateTokens = candidate.split("\\s+");

        if (queryTokens.length == 0 || candidateTokens.length == 0) return 0;

        int totalScore = 0;
        int matchedTokens = 0;

        for (String qt : queryTokens) {
            int bestTokenScore = 0;
            for (String ct : candidateTokens) {
                bestTokenScore = Math.max(bestTokenScore, levenshteinSimilarity(qt, ct));
            }
            totalScore += bestTokenScore;
            if (bestTokenScore >= 80) matchedTokens++;
        }

        int avgScore = totalScore / queryTokens.length;

        // Penalize if token count differs significantly
        int tokenCountDiff = Math.abs(queryTokens.length - candidateTokens.length);
        if (tokenCountDiff > 1) {
            avgScore = (int) (avgScore * 0.85);
        }

        return avgScore;
    }

    // --- Soundex (American Soundex algorithm) ---

    public String soundex(String input) {
        if (input == null || input.isEmpty()) return "0000";

        // Use only alphabetic characters
        String clean = input.replaceAll("[^a-zA-Z]", "").toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) return "0000";

        StringBuilder result = new StringBuilder();
        result.append(clean.charAt(0));

        char prevCode = soundexCode(clean.charAt(0));

        for (int i = 1; i < clean.length() && result.length() < 4; i++) {
            char code = soundexCode(clean.charAt(i));
            if (code != '0' && code != prevCode) {
                result.append(code);
            }
            prevCode = code;
        }

        while (result.length() < 4) {
            result.append('0');
        }

        return result.toString();
    }

    private char soundexCode(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'B', 'F', 'P', 'V' -> '1';
            case 'C', 'G', 'J', 'K', 'Q', 'S', 'X', 'Z' -> '2';
            case 'D', 'T' -> '3';
            case 'L' -> '4';
            case 'M', 'N' -> '5';
            case 'R' -> '6';
            default -> '0';
        };
    }

    // --- Name normalization ---

    public String normalize(String name) {
        if (name == null) return "";
        String lower = name.trim().toLowerCase(Locale.ROOT);
        // Decompose Unicode characters, then strip combining marks (diacritics)
        String decomposed = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD);
        return decomposed
                .replaceAll("[\\p{M}]", "")       // strip combining diacritical marks
                .replaceAll("[^a-z0-9\\s]", " ")  // non-alphanumeric to space
                .replaceAll("\\s+", " ")           // collapse whitespace
                .trim();
    }

    public record MatchResult(int score, String matchType) {
    }
}
