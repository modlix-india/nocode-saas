package com.fincity.saas.commons.core.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The Porter (1980) stemming algorithm, for MySQL full-text search.
 *
 * InnoDB FULLTEXT has no stemming and never has - MySQL's own WL#2423 has been
 * open for years - so a search for "guides" does not find "guide". Mongo's
 * {@code $text} stems through its default english analyzer, so the same search
 * on the same rows answers differently depending on which backend the app sits
 * on. This closes that gap on the MySQL side.
 *
 * Hand written rather than pulled in: the platform carries no Lucene, Snowball,
 * OpenNLP or ICU, and a dependency for one well specified, public domain
 * algorithm is the wrong trade.
 *
 * <b>This is Porter, not Porter2.</b> Mongo uses Snowball (Porter2), which
 * differs on a minority of words - most visibly Porter's habit of leaving a
 * bare "i" where Porter2 keeps "y". The aim here is to close the plural and
 * gerund gap, not to agree with Mongo token for token, and chasing the latter
 * would mean carrying a second algorithm to no real benefit.
 *
 * A stem is not a word. "ponies" stems to "poni" and "agreed" to "agre"; that
 * is correct and expected, because the stem is only ever compared with other
 * stems - or, here, used as a search prefix.
 */
public final class PorterStemmer {

    private PorterStemmer() {}

    /** Characters MySQL boolean mode reads as operators, so never part of a term. */
    private static final String BOOLEAN_OPERATORS = "+-><()~*\"@";

    /**
     * One token, stemmed. Returns lowercase, and returns the input unchanged
     * when it is too short for the algorithm to say anything useful about.
     */
    public static String stem(String word) {

        if (word == null) return "";

        String lower = word.trim().toLowerCase();

        // Porter is undefined below three letters, and the two letter words it
        // would touch ("is", "as") are ones no search benefits from altering.
        if (lower.length() < 3) return lower;

        for (int i = 0; i < lower.length(); i++)
            if (!Character.isLetter(lower.charAt(i))) return lower;

        return new Work(lower).stem();
    }

    /**
     * The longest prefix of {@code word} that its stem still agrees with.
     *
     * A stem is NOT always a prefix of the word it came from, and that matters
     * here because the MySQL side searches with the truncation operator: it
     * sends {@code <term>*} and relies on stored words starting with it. Porter
     * step 1c rewrites a trailing y to i, so "quarterly" stems to "quarterli" -
     * and "quarterli*" matches nothing at all, turning a search that works
     * today into one that silently returns no rows.
     *
     * Taking the common prefix instead keeps the widening that stemming is for
     * while guaranteeing the result is something stored text can start with:
     * "quarterly" gives "quarterl", which still finds quarterly and
     * quarterlies, and "guides" gives the full stem "guid".
     */
    public static String searchPrefix(String word) {

        String token = word == null ? "" : word.trim().toLowerCase();
        if (token.isEmpty()) return "";

        String stemmed = stem(token);

        int i = 0;
        while (i < stemmed.length() && i < token.length() && stemmed.charAt(i) == token.charAt(i)) i++;

        return token.substring(0, i);
    }

    /**
     * A whole query, split into search prefixes.
     *
     * Splits on anything that is not a letter or a digit, which also removes
     * every boolean mode operator as a side effect - a search for "C++" must
     * not reach the SQL carrying two "+" operators.
     */
    public static List<String> searchPrefixes(String query) {

        List<String> out = new ArrayList<>();
        if (query == null || query.isBlank()) return out;

        for (String raw : query.split("[^\\p{L}\\p{N}]+")) {
            String token = strip(raw);
            if (token.isEmpty()) continue;

            String prefix = searchPrefix(token);
            if (!prefix.isEmpty()) out.add(prefix);
        }

        return out;
    }

    /** Belt and braces: the split should already have removed these. */
    private static String strip(String token) {
        StringBuilder sb = new StringBuilder(token.length());
        for (char ch : token.toCharArray()) if (BOOLEAN_OPERATORS.indexOf(ch) < 0) sb.append(ch);
        return sb.toString().trim();
    }

    /**
     * Porter's reference implementation, which is written against a mutable
     * buffer with two cursors: {@code k} is the last character of the word and
     * {@code j} the last character of the stem a rule is testing. Kept in that
     * shape deliberately - it is the form the algorithm is specified in, and a
     * tidier rewrite is a rewrite nobody can check against the paper.
     */
    private static final class Work {

        // Slack, because a rule may lengthen the word: AT -> ATE, BL -> BLE.
        private final char[] b;
        private int k;
        private int j;

        Work(String word) {
            this.b = new char[word.length() + 4];
            word.getChars(0, word.length(), this.b, 0);
            this.k = word.length() - 1;
        }

        String stem() {
            step1ab();
            if (k > 0) {
                step1c();
                step2();
                step3();
                step4();
                step5();
            }
            return new String(b, 0, k + 1);
        }

        private boolean consonant(int i) {
            return switch (b[i]) {
                case 'a', 'e', 'i', 'o', 'u' -> false;
                case 'y' -> i == 0 || !consonant(i - 1);
                default -> true;
            };
        }

        /** The number of consonant-vowel sequences in b[0..j]. Porter's "m". */
        private int measure() {
            int n = 0;
            int i = 0;
            while (true) {
                if (i > j) return n;
                if (!consonant(i)) break;
                i++;
            }
            i++;
            while (true) {
                while (true) {
                    if (i > j) return n;
                    if (consonant(i)) break;
                    i++;
                }
                i++;
                n++;
                while (true) {
                    if (i > j) return n;
                    if (!consonant(i)) break;
                    i++;
                }
                i++;
            }
        }

        private boolean vowelInStem() {
            for (int i = 0; i <= j; i++) if (!consonant(i)) return true;
            return false;
        }

        private boolean doubleConsonant(int i) {
            if (i < 1 || b[i] != b[i - 1]) return false;
            return consonant(i);
        }

        /**
         * consonant-vowel-consonant, where the last is not w, x or y. Marks a
         * short word, which takes an "e" back in step 1b: hop -> hopping.
         */
        private boolean cvc(int i) {
            if (i < 2 || !consonant(i) || consonant(i - 1) || !consonant(i - 2)) return false;
            char ch = b[i];
            return ch != 'w' && ch != 'x' && ch != 'y';
        }

        private boolean ends(String s) {
            int length = s.length();
            int offset = k - length + 1;
            if (offset < 0) return false;
            for (int i = 0; i < length; i++) if (b[offset + i] != s.charAt(i)) return false;
            j = k - length;
            return true;
        }

        /** Write s over the suffix that {@link #ends} just matched. */
        private void setTo(String s) {
            int length = s.length();
            s.getChars(0, length, b, j + 1);
            k = j + length;
        }

        private void replaceIfMeasured(String s) {
            if (measure() > 0) setTo(s);
        }

        private void step1ab() {

            if (b[k] == 's') {
                if (ends("sses") || ends("ies")) k -= 2;
                else if (b[k - 1] != 's') k--;
            }

            if (ends("eed")) {
                if (measure() > 0) k--;
                return;
            }

            if ((ends("ed") || ends("ing")) && vowelInStem()) {
                k = j;

                if (ends("at")) setTo("ate");
                else if (ends("bl")) setTo("ble");
                else if (ends("iz")) setTo("ize");
                else if (doubleConsonant(k)) {
                    k--;
                    char ch = b[k];
                    if (ch == 'l' || ch == 's' || ch == 'z') k++;
                } else if (measure() == 1 && cvc(k)) setTo("e");
            }
        }

        /** A terminal y becomes i when the stem holds a vowel: happy -> happi. */
        private void step1c() {
            if (ends("y") && vowelInStem()) b[k] = 'i';
        }

        private void step2() {
            if (k == 0) return;
            switch (b[k - 1]) {
                case 'a' -> {
                    if (ends("ational")) replaceIfMeasured("ate");
                    else if (ends("tional")) replaceIfMeasured("tion");
                }
                case 'c' -> {
                    if (ends("enci")) replaceIfMeasured("ence");
                    else if (ends("anci")) replaceIfMeasured("ance");
                }
                case 'e' -> {
                    if (ends("izer")) replaceIfMeasured("ize");
                }
                case 'l' -> {
                    if (ends("bli")) replaceIfMeasured("ble");
                    else if (ends("alli")) replaceIfMeasured("al");
                    else if (ends("entli")) replaceIfMeasured("ent");
                    else if (ends("eli")) replaceIfMeasured("e");
                    else if (ends("ousli")) replaceIfMeasured("ous");
                }
                case 'o' -> {
                    if (ends("ization")) replaceIfMeasured("ize");
                    else if (ends("ation") || ends("ator")) replaceIfMeasured("ate");
                }
                case 's' -> {
                    if (ends("alism")) replaceIfMeasured("al");
                    else if (ends("iveness")) replaceIfMeasured("ive");
                    else if (ends("fulness")) replaceIfMeasured("ful");
                    else if (ends("ousness")) replaceIfMeasured("ous");
                }
                case 't' -> {
                    if (ends("aliti")) replaceIfMeasured("al");
                    else if (ends("iviti")) replaceIfMeasured("ive");
                    else if (ends("biliti")) replaceIfMeasured("ble");
                }
                case 'g' -> {
                    if (ends("logi")) replaceIfMeasured("log");
                }
                default -> {
                    // No step 2 rule ends in this letter.
                }
            }
        }

        private void step3() {
            switch (b[k]) {
                case 'e' -> {
                    if (ends("icate")) replaceIfMeasured("ic");
                    else if (ends("ative")) replaceIfMeasured("");
                    else if (ends("alize")) replaceIfMeasured("al");
                }
                case 'i' -> {
                    if (ends("iciti")) replaceIfMeasured("ic");
                }
                case 'l' -> {
                    if (ends("ical")) replaceIfMeasured("ic");
                    else if (ends("ful")) replaceIfMeasured("");
                }
                case 's' -> {
                    if (ends("ness")) replaceIfMeasured("");
                }
                default -> {
                    // No step 3 rule ends in this letter.
                }
            }
        }

        private void step4() {
            if (k == 0) return;

            boolean matched =
                    switch (b[k - 1]) {
                        case 'a' -> ends("al");
                        case 'c' -> ends("ance") || ends("ence");
                        case 'e' -> ends("er");
                        case 'i' -> ends("ic");
                        case 'l' -> ends("able") || ends("ible");
                        case 'n' -> ends("ant") || ends("ement") || ends("ment") || ends("ent");
                        case 'o' -> (ends("ion") && j >= 0 && (b[j] == 's' || b[j] == 't')) || ends("ou");
                        case 's' -> ends("ism");
                        case 't' -> ends("ate") || ends("iti");
                        case 'u' -> ends("ous");
                        case 'v' -> ends("ive");
                        case 'z' -> ends("ize");
                        default -> false;
                    };

            if (matched && measure() > 1) k = j;
        }

        private void step5() {

            j = k;

            if (b[k] == 'e') {
                int m = measure();
                if (m > 1 || (m == 1 && !cvc(k - 1))) k--;
            }

            if (b[k] == 'l' && doubleConsonant(k)) {
                j = k;
                if (measure() > 1) k--;
            }
        }
    }
}
