package com.fincity.saas.message.util;

import com.fincity.saas.message.model.message.whatsapp.webhook.IMessage;

/**
 * Pulls the searchable text out of an inbound WhatsApp message.
 *
 * <p>Extracted here rather than by the consumer so the owning service never has to understand a
 * Meta payload, and stored as a plain column so search can use an index instead of digging through
 * JSON at query time. Search across conversations is the reason message storage moved out of this
 * service at all, so a message arriving with no extractable text is simply unfindable by content.
 *
 * <p>Captions count as text. Someone searching "brochure" expects to find the image they sent with
 * that caption, not just the messages that were plain text.
 */
public final class WhatsappBodyText {

    private WhatsappBodyText() {}

    public static String of(IMessage message) {

        if (message == null) return null;

        if (message.getText() != null && message.getText().getBody() != null)
            return blankToNull(message.getText().getBody());

        if (message.getImage() != null) return blankToNull(message.getImage().getCaption());
        if (message.getVideo() != null) return blankToNull(message.getVideo().getCaption());
        if (message.getDocument() != null) return blankToNull(message.getDocument().getCaption());

        // A button reply is what the customer chose, so its label is the most meaningful thing
        // they "said" and belongs in search alongside typed text.
        if (message.getButton() != null) return blankToNull(message.getButton().getText());

        if (message.getReaction() != null) return blankToNull(message.getReaction().getEmoji());

        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
