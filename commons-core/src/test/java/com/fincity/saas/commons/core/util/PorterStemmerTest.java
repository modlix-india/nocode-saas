package com.fincity.saas.commons.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The algorithm is ported by hand, so it is pinned against the examples in
 * Porter's own paper rather than against what this implementation happens to
 * do. Every pair below is quoted from the published rule set; if one of them
 * fails the port is wrong, not the test.
 */
@DisplayName("Porter stemmer")
class PorterStemmerTest {

    private static void stems(String word, String expected) {
        assertEquals(expected, PorterStemmer.stem(word), word);
    }

    @Nested
    @DisplayName("the published examples, step by step")
    class Published {

        @Test
        @DisplayName("step 1a, plurals")
        void step1a() {
            stems("caresses", "caress");
            stems("ponies", "poni");
            stems("ties", "ti");
            stems("caress", "caress");
            stems("cats", "cat");
        }

        @Test
        @DisplayName("step 1b, past tense and gerunds")
        void step1b() {
            stems("feed", "feed");
            stems("agreed", "agre");
            stems("plastered", "plaster");
            stems("bled", "bled");
            stems("motoring", "motor");
            stems("sing", "sing");
        }

        @Test
        @DisplayName("step 1b cleanup, the AT / BL / IZ and double-consonant rules")
        void step1bCleanup() {
            stems("conflated", "conflat");
            stems("troubled", "troubl");
            stems("sized", "size");
            stems("hopping", "hop");
            stems("tanned", "tan");
            stems("falling", "fall");
            stems("hissing", "hiss");
            stems("fizzed", "fizz");
            stems("failing", "fail");
            stems("filing", "file");
        }

        @Test
        @DisplayName("step 1c, y becomes i")
        void step1c() {
            stems("happy", "happi");
            stems("sky", "sky");
        }

        @Test
        @DisplayName("step 2")
        void step2() {
            stems("relational", "relat");
            stems("conditional", "condit");
            stems("valenci", "valenc");
            stems("digitizer", "digit");
            stems("conformabli", "conform");
            stems("radicalli", "radic");
            stems("differentli", "differ");
            stems("analogousli", "analog");
            stems("predication", "predic");
            stems("operator", "oper");
            stems("feudalism", "feudal");
            stems("decisiveness", "decis");
            stems("hopefulness", "hope");
            stems("formaliti", "formal");
            stems("sensitiviti", "sensit");
        }

        @Test
        @DisplayName("step 3")
        void step3() {
            stems("triplicate", "triplic");
            stems("formative", "form");
            stems("formalize", "formal");
            stems("electriciti", "electr");
            stems("electrical", "electr");
            stems("hopeful", "hope");
            stems("goodness", "good");
        }

        @Test
        @DisplayName("step 4")
        void step4() {
            stems("revival", "reviv");
            stems("allowance", "allow");
            stems("inference", "infer");
            stems("airliner", "airlin");
            stems("gyroscopic", "gyroscop");
            stems("adjustable", "adjust");
            stems("defensible", "defens");
            stems("irritant", "irrit");
            stems("replacement", "replac");
            stems("adjustment", "adjust");
            stems("dependent", "depend");
            stems("adoption", "adopt");
            stems("homologou", "homolog");
            stems("communism", "commun");
            stems("activate", "activ");
            stems("angulariti", "angular");
            stems("homologous", "homolog");
            stems("effective", "effect");
            stems("bowdlerize", "bowdler");
        }

        @Test
        @DisplayName("step 5, the trailing e and double l")
        void step5() {
            stems("probate", "probat");
            stems("rate", "rate");
            stems("cease", "ceas");
            stems("controll", "control");
            stems("roll", "roll");
        }
    }

    @Nested
    @DisplayName("the behaviour the search path depends on")
    class ForSearch {

        /**
         * The whole point of the exercise: the forms a user types and the form
         * stored in the row have to reach the same prefix.
         */
        @Test
        @DisplayName("the plural, gerund and past forms share a stem with the base word")
        void inflectionsConverge() {
            assertEquals(PorterStemmer.stem("guide"), PorterStemmer.stem("guides"));
            assertEquals(PorterStemmer.stem("plan"), PorterStemmer.stem("planning"));
            assertEquals(PorterStemmer.stem("expansion"), PorterStemmer.stem("expansions"));
        }

        /**
         * A stem is used as a SEARCH PREFIX, so it must be a prefix of the
         * words it is meant to find. Porter only ever removes or rewrites a
         * suffix, but the rewriting rules (AT -> ATE) can break that, and this
         * is the property the MySQL side actually relies on.
         */
        @Test
        @DisplayName("the stem is NOT always a prefix, which is why searchPrefix exists")
        void stemIsNotAlwaysAPrefix() {
            // Step 1c rewrites a trailing y to i. Sent to MySQL as "quarterli*"
            // this matches nothing, so a search that works today would quietly
            // start returning no rows.
            assertEquals("quarterli", PorterStemmer.stem("quarterly"));
            assertFalse("quarterly".startsWith(PorterStemmer.stem("quarterly")));
        }

        @Test
        @DisplayName("searchPrefix is always a prefix of the word")
        void searchPrefixIsAPrefix() {
            for (String word :
                    List.of("guides", "planning", "expansions", "quarterly", "revenues", "reporting", "happy"))
                assertTrue(
                        word.startsWith(PorterStemmer.searchPrefix(word)),
                        word + " -> " + PorterStemmer.searchPrefix(word) + " is not a prefix");
        }

        @Test
        @DisplayName("searchPrefix still widens across the inflections")
        void searchPrefixStillWidens() {
            // The prefix a search sends must match the stored base word.
            assertTrue("guide".startsWith(PorterStemmer.searchPrefix("guides")));
            assertTrue("plans".startsWith(PorterStemmer.searchPrefix("planning")));
            assertTrue("expansion".startsWith(PorterStemmer.searchPrefix("expansions")));
            assertTrue("quarterly".startsWith(PorterStemmer.searchPrefix("quarterly")));
        }

        @Test
        @DisplayName("stemming twice changes nothing")
        void idempotent() {
            for (String word : List.of("caresses", "ponies", "plastered", "formalize", "adjustable")) {
                String once = PorterStemmer.stem(word);
                assertEquals(once, PorterStemmer.stem(once), word);
            }
        }

        @Test
        @DisplayName("short, empty and non-letter input is returned untouched")
        void shortAndOddInput() {
            stems("", "");
            stems("a", "a");
            stems("is", "is");
            stems("c3po", "c3po");
            assertEquals("", PorterStemmer.stem(null));
        }

        @Test
        @DisplayName("case and surrounding space do not matter")
        void caseAndSpace() {
            stems("  GUIDES  ", "guid");
            stems("Guides", "guid");
        }
    }

    @Nested
    @DisplayName("stemQuery")
    class Query {

        @Test
        @DisplayName("splits a phrase into stemmed terms")
        void splitsAndStems() {
            assertEquals(List.of("southern", "region"), PorterStemmer.searchPrefixes("southern regions"));
        }

        /**
         * The reason this lives here rather than at the call site: boolean mode
         * reads + - > < ( ) ~ * " @ as operators, so a search for "C++" would
         * otherwise reach MySQL as a syntax error. The same class of bug as an
         * unescaped regex.
         */
        @Test
        @DisplayName("boolean mode operators never survive tokenising")
        void operatorsAreStripped() {
            for (String term : PorterStemmer.searchPrefixes("C++ >best< (thing) ~x* \"quoted\" a@b"))
                for (char ch : "+-><()~*\"@".toCharArray())
                    assertFalse(term.indexOf(ch) >= 0, "'" + term + "' still carries " + ch);
        }

        @Test
        @DisplayName("a query of only punctuation yields no terms at all")
        void nothingUsable() {
            assertTrue(PorterStemmer.searchPrefixes("+++ --- ***").isEmpty());
            assertTrue(PorterStemmer.searchPrefixes("   ").isEmpty());
            assertTrue(PorterStemmer.searchPrefixes(null).isEmpty());
        }

        @Test
        @DisplayName("digits survive, because a part number is a legitimate search")
        void digitsSurvive() {
            assertEquals(List.of("abc123"), PorterStemmer.searchPrefixes("abc123"));
        }
    }
}
