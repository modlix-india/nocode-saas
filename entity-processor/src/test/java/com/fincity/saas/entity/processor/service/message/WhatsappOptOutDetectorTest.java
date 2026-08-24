package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Opt-out detection, tested from both directions because both directions are expensive.
 *
 * <p>A missed opt-out means we keep messaging somebody who asked us to stop, which is the complaint
 * that gets a linked number banned with no appeal. A false positive silently ends automated contact
 * with a live deal. The false-negative cases here are the ones that matter more, but the
 * false-positive cases are the ones a naive substring match actually fails.
 */
class WhatsappOptOutDetectorTest {

    @ParameterizedTest
    @DisplayName("recognises a lead asking to stop")
    @ValueSource(
            strings = {
                "STOP",
                "stop",
                "Stop.",
                "please stop",
                "Stop messaging me",
                "unsubscribe",
                "Please unsubscribe me",
                "opt out",
                "OPTOUT",
                "remove me",
                "do not contact me again",
                "don't contact me",
                "dont message me",
                "not interested",
                "Not interested, thanks",
                "leave me alone",
                "no more messages please",
                // Romanised Hindi, which is how most of this arrives on an Indian keyboard.
                "band karo",
                "Band karo please",
                "message mat bhejo",
                "mat bhejiye",
                "nahi chahiye",
                "pareshan mat karo",
                // Devanagari, because people switch script mid-conversation.
                "बंद करो",
                "मत भेजो",
                "नहीं चाहिए"
            })
    void detectsOptOut(String body) {
        assertTrue(WhatsappOptOutDetector.isOptOut(body), () -> "should have been read as an opt-out: " + body);
    }

    @ParameterizedTest
    @DisplayName("does not fire on ordinary messages that merely contain the words")
    @ValueSource(
            strings = {
                // The case that breaks a substring match, and the reason boundaries are checked.
                "Can I stop by the site on Sunday?",
                "I'll stop by tomorrow around 4",
                "Is there a bus stop nearby?",
                "It's a one-stop shop for everything",
                "Please send me the stopover details",
                "Yes I am interested",
                "Very interested, please send the brochure",
                "What is the price?",
                "Sounds good, thanks",
                // Contains "remove" but is not "remove me".
                "Can you remove the parking charge from the quote?",
                // Contains "message" and "mat" but neither phrase.
                "Send the message on this number",
                ""
            })
    void ignoresOrdinaryMessages(String body) {
        assertFalse(WhatsappOptOutDetector.isOptOut(body), () -> "should not have been read as an opt-out: " + body);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null is not an opt-out")
    void ignoresNull() {
        assertFalse(WhatsappOptOutDetector.isOptOut(null));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("a long message is a conversation, not an opt-out")
    void ignoresLongMessages() {
        // An opt-out is short and blunt. A paragraph that happens to contain "stop" is somebody
        // talking to us, and reading it as an opt-out would end a live deal on a coincidence.
        String essay = "Thanks for the details. We visited the site last weekend and my wife liked the "
                + "east facing unit, though we could not stop for long because of the rain. Could you "
                + "share the payment plan and let me know if the corner unit is still available?";

        assertTrue(essay.length() > 160, "the fixture must actually exceed the length limit");
        assertFalse(WhatsappOptOutDetector.isOptOut(essay));
    }
}
