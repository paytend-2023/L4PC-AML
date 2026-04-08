package com.paytend.l4pc.aml.service.matching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NameMatcherTest {

    private final NameMatcher matcher = new NameMatcher();

    @Test
    void exactMatch() {
        NameMatcher.MatchResult result = matcher.score("John Smith", "John Smith");
        assertEquals(100, result.score());
        assertEquals("EXACT", result.matchType());
    }

    @Test
    void caseInsensitiveMatch() {
        NameMatcher.MatchResult result = matcher.score("JOHN SMITH", "john smith");
        assertEquals(100, result.score());
    }

    @Test
    void fuzzyMatch() {
        NameMatcher.MatchResult result = matcher.score("Jon Smith", "John Smith");
        assertTrue(result.score() >= 80, "Expected >= 80 but got " + result.score());
    }

    @Test
    void soundexMatch() {
        // Robert and Rupert have same Soundex: R163
        String s1 = matcher.soundex("Robert");
        String s2 = matcher.soundex("Rupert");
        assertEquals(s1, s2);
    }

    @Test
    void soundexDifferent() {
        String s1 = matcher.soundex("John");
        String s2 = matcher.soundex("Mary");
        assertNotEquals(s1, s2);
    }

    @Test
    void levenshteinIdentical() {
        assertEquals(0, matcher.levenshteinDistance("test", "test"));
        assertEquals(100, matcher.levenshteinSimilarity("test", "test"));
    }

    @Test
    void levenshteinOneEdit() {
        assertEquals(1, matcher.levenshteinDistance("cat", "car"));
    }

    @Test
    void tokenReorderMatch() {
        // "Smith John" should match "John Smith" reasonably well
        NameMatcher.MatchResult result = matcher.score("Smith John", "John Smith");
        assertTrue(result.score() >= 85, "Expected >= 85 but got " + result.score());
    }

    @Test
    void nullHandling() {
        assertEquals(0, matcher.score(null, "John").score());
        assertEquals(0, matcher.score("John", null).score());
        assertEquals(0, matcher.score(null, null).score());
    }

    @Test
    void normalization() {
        assertEquals("john smith", matcher.normalize("  John  Smith  "));
        assertEquals("jose garcia", matcher.normalize("José García"));
    }

    @Test
    void completelyDifferentNames() {
        NameMatcher.MatchResult result = matcher.score("Alexander Petrov", "Maria Gonzalez");
        assertTrue(result.score() < 50, "Expected < 50 but got " + result.score());
    }
}
