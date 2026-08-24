package com.fincity.saas.entity.processor.service.message;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises a lead asking us to stop.
 *
 * <p>Not optional, and not a nicety. Continuing to message somebody who has asked us not to is the
 * complaint that becomes a report, and a report against a linked number is a ban with no appeal. The
 * cost of a missed opt-out is somebody's business number; the cost of a false positive is a deal that
 * stops receiving automated messages until a person clears the flag. Those are not comparable, so
 * this errs toward detecting.
 *
 * <p><b>Two kinds of match, and the distinction is the whole design.</b> A bare keyword like "stop"
 * only counts when it is the entire message. Matching it anywhere, even as a whole word, flags "can I
 * stop by the site on Sunday?" and "is there a bus stop nearby?", which are messages from leads who
 * are actively interested. Longer phrases like "stop messaging" carry their own intent and are matched
 * anywhere in the text.
 *
 * <p>That split is not a refinement added later. It is the difference between a working feature and
 * one that silently kills live deals, and it was found by a test rather than by reasoning, which is
 * why the cases are pinned in {@code WhatsappOptOutDetectorTest}.
 *
 * <p>Hindi and Hinglish are included because the first market is India and a lead who writes "band
 * karo" has asked exactly as clearly as one who writes "stop". Both scripts are matched: people
 * switch between Devanagari and romanised Hindi mid-conversation, often mid-sentence.
 */
public final class WhatsappOptOutDetector {

    /**
     * Keywords that only count as the whole message.
     *
     * <p>"STOP" alone is unambiguous, and it is the convention every messaging service has trained
     * people to expect. The same word inside a sentence carries its ordinary English meaning far more
     * often than not.
     */
    private static final List<String> STANDALONE = List.of("stop", "unsubscribe", "opt out", "optout", "opt-out");

    /**
     * Politeness and address words stripped from both ends before the standalone check.
     *
     * <p>Nobody types a bare "STOP". They type "please stop" or "unsubscribe me", and treating those
     * as different from the keyword would mean the feature only worked for people who happened to be
     * curt.
     */
    private static final List<String> FILLERS =
            List.of("please", "pls", "plz", "kindly", "thanks", "thank", "you", "me", "now", "ok", "okay", "sir",
                    "madam", "bhai", "ji");

    /**
     * Phrases matched as whole phrases, case-insensitively, anywhere in the message.
     *
     * <p>Every entry here is long enough that its presence is intent regardless of what surrounds it.
     * Anything short enough to appear innocently belongs in {@link #STANDALONE} instead.
     */
    private static final List<String> PHRASES = List.of(
            "stop messaging",
            "stop sending",
            "stop contacting",
            "remove me",
            "do not contact",
            "don't contact",
            "dont contact",
            "do not message",
            "don't message",
            "dont message",
            "do not disturb",
            "not interested",
            "no more messages",
            "leave me alone",

            // Romanised Hindi, which is how most of this actually arrives on a phone keyboard.
            "band karo",
            "band karein",
            "bandh karo",
            "mat bhejo",
            "mat bhejiye",
            "message mat",
            "pareshan mat",
            "nahi chahiye",
            "nahin chahiye",

            // Devanagari.
            "बंद करो",
            "बंद करें",
            "मत भेजो",
            "मत भेजिये",
            "नहीं चाहिए",
            "परेशान मत");

    private static final Pattern PATTERN = Pattern.compile(
            PHRASES.stream().map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElse("(?!)"),
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private WhatsappOptOutDetector() {
        // Static matcher only.
    }

    /**
     * Whether this inbound text reads as a request to stop.
     *
     * <p>Long messages are deliberately not scanned past a limit. An opt-out is short and blunt by
     * nature; a three-paragraph reply that happens to contain the word "stop" is a conversation, and
     * treating it as an opt-out would silently end a live deal.
     */
    public static boolean isOptOut(String body) {

        if (body == null || body.isBlank()) return false;

        String text = body.trim();
        if (text.length() > 160) return false;

        String lower = text.toLowerCase(Locale.ROOT);

        return matchesStandalone(lower) || containsPhrase(lower);
    }

    /**
     * Whether the message, stripped down, <em>is</em> one of the bare keywords.
     *
     * <p>Punctuation goes first, then politeness words from either end, so "Please stop." and
     * "unsubscribe me, thanks" both reduce to the keyword itself. What is left has to match exactly:
     * anything with real content still in it is a sentence, and a sentence containing "stop" is
     * usually somebody proposing to stop by.
     */
    private static boolean matchesStandalone(String lower) {

        String stripped = lower.replaceAll("[^\\p{L}\\p{N}\\s-]", " ").trim().replaceAll("\\s+", " ");

        List<String> words = new java.util.ArrayList<>(List.of(stripped.isEmpty() ? new String[0] : stripped.split(" ")));

        while (!words.isEmpty() && FILLERS.contains(words.get(0))) words.remove(0);
        while (!words.isEmpty() && FILLERS.contains(words.get(words.size() - 1))) words.remove(words.size() - 1);

        return STANDALONE.contains(String.join(" ", words));
    }

    /** Whether a phrase appears anywhere, delimited by non-letters on both sides. */
    private static boolean containsPhrase(String lower) {

        // Padded on both ends so a phrase at the very start or end still has a character either
        // side to test against.
        String padded = " " + lower + " ";

        return PATTERN.matcher(padded)
                .results()
                // Boundaries checked by hand rather than with \b, which is defined over ASCII word
                // characters and does the wrong thing against Devanagari.
                .anyMatch(match -> !isWordChar(padded.charAt(match.start() - 1))
                        && !isWordChar(padded.charAt(match.end())));
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }
}
